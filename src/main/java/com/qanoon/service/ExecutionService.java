package com.qanoon.service;

import com.qanoon.common.AuditService;
import com.qanoon.common.BusinessException;
import com.qanoon.common.EnumParser;
import com.qanoon.common.ForbiddenException;
import com.qanoon.common.NotFoundException;
import com.qanoon.common.NotificationService;
import com.qanoon.common.NumberService;
import com.qanoon.common.PageResult;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.SettingService;
import com.qanoon.common.StorageService;
import com.qanoon.domain.Attachment;
import com.qanoon.domain.CurrentUserHolder;
import com.qanoon.domain.Enums;
import com.qanoon.domain.ExecutionFile;
import com.qanoon.domain.ExecutionOrder;
import com.qanoon.domain.LegalCase;
import com.qanoon.domain.Party;
import com.qanoon.domain.Payment;
import com.qanoon.domain.Permission;
import com.qanoon.domain.User;
import com.qanoon.dto.ExecutionDtos;
import com.qanoon.repo.ExecutionFileRepository;
import com.qanoon.repo.ExecutionOrderRepository;
import com.qanoon.repo.LegalCaseRepository;
import com.qanoon.repo.PaymentRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * مسار التنفيذ: إنشاء ملف التنفيذ من القضية، أوامر التنفيذ الخمسة،
 * الدفعات على الملف، بوابة الاستيفاء وشهادة الاستيفاء المرقمة.
 *
 * القواعد الملزمة:
 * - يجوز أن يكون أكثر من أمر تنفيذ ساري في نفس الوقت، ولا يُمنع التعدد.
 * - كل أمر يُسجَّل في الخط الزمني للملف بتاريخه وحالته.
 * - المبلغ المحصّل يُشتق من الدفعات المؤكدة فقط.
 * - لا يُغلق الملف إلا إذا كان المتبقي صفراً.
 */
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ENTITY = "ExecutionFile";
    private static final String ORDER_ENTITY = "ExecutionOrder";
    private static final String EXEC_TYPE = "EXECUTION";
    private static final String CASE_TYPE = "CASE";
    private static final String SATISFACTION_REASON = "اكتمال الاستيفاء";

    private final ExecutionFileRepository executionFileRepository;
    private final ExecutionOrderRepository executionOrderRepository;
    private final PaymentRepository paymentRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final UserRepository userRepository;
    private final NumberService numberService;
    private final StorageService storageService;
    private final SettingService settingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ArchiveService archiveService;
    private final SecurityUtils securityUtils;

    // ================================================================= إنشاء الملف من القضية

    /**
     * تُستدعى من مسار تحويل القضية إلى التنفيذ بعد اجتياز الشروط الستة.
     * تنشئ ملف تنفيذ برقم جديد، تنسخ أطراف القضية والمحامي المسند والمحكمة ومبلغ الحكم،
     * وتنسخ كل مرفقات القضية إلى ملف التنفيذ، وتسجّل العملية في سجل النشاطات.
     */
    @Transactional
    public ExecutionFile createFromCase(com.qanoon.domain.LegalCase c) {
        if (c == null || c.getId() == null) {
            throw new BusinessException("لا يمكن إنشاء ملف تنفيذ بدون قضية محفوظة");
        }
        if (executionFileRepository.findByCaseId(c.getId()).isPresent()) {
            throw new BusinessException("سبق تحويل هذه القضية إلى التنفيذ — لا يجوز إنشاء ملف تنفيذ ثانٍ لنفس القضية");
        }
        if (c.getClient() == null || c.getOpponent() == null) {
            throw new BusinessException("لا يمكن إنشاء ملف التنفيذ — بيانات الموكل أو الخصم ناقصة في القضية");
        }

        ExecutionFile f = new ExecutionFile();
        f.setExecutionNumber(numberService.next("EXE", "EXE"));
        f.setCaseId(c.getId());
        f.setClient(c.getClient());
        f.setDebtor(c.getOpponent());
        f.setAssignedLawyer(c.getAssignedLawyer());
        f.setCourt(c.getCourt());
        f.setJudgmentAmount(firstNonNull(c.getJudgmentAmount(), c.getClaimAmount()));
        f.setExpensesAmount(BigDecimal.ZERO);
        f.setCollectedAmount(BigDecimal.ZERO);
        f.setStatus(Enums.ExecStatus.OPEN);
        f.setOpenedAt(LocalDate.now());
        f.setNotes(cut("محوّل من القضية رقم " + nz(c.getCaseNumber())
                + (c.getJudgmentNumber() != null ? " — الحكم رقم " + c.getJudgmentNumber() : "")
                + (c.getSubject() != null ? " — " + c.getSubject() : ""), 2000));
        ExecutionFile saved = executionFileRepository.save(f);

        List<Attachment> copied = storageService.copyAll(CASE_TYPE, c.getId(), EXEC_TYPE, saved.getId());
        int copiedCount = copied == null ? 0 : copied.size();

        auditService.log("EXECUTION_CREATE", ENTITY, saved.getId(), saved.getExecutionNumber(),
                "إنشاء ملف التنفيذ " + saved.getExecutionNumber() + " من القضية " + nz(c.getCaseNumber())
                        + " بتاريخ " + LocalDateTime.now().format(DT) + " بواسطة " + actorName()
                        + " — الموكل: " + saved.getClient().getName()
                        + "، المدين: " + saved.getDebtor().getName()
                        + "، مبلغ الحكم: " + money(saved.getJudgmentAmount()) + " " + currency()
                        + "، ونُسخ " + copiedCount + " مرفقاً من القضية إلى ملف التنفيذ.");

        if (saved.getAssignedLawyer() != null) {
            notificationService.push(saved.getAssignedLawyer().getId(), Enums.NotificationType.TASK,
                    "ملف تنفيذ جديد مسند إليك",
                    "تم فتح ملف التنفيذ " + saved.getExecutionNumber() + " ضد " + saved.getDebtor().getName()
                            + " بمبلغ " + money(saved.getJudgmentAmount()) + " " + currency(),
                    EXEC_TYPE, saved.getId(), null, "EXE-NEW-" + saved.getId());
        }
        return saved;
    }

    // ================================================================= قراءة

    @Transactional(readOnly = true)
    public PageResult search(String status, String q, int page, int size) {
        securityUtils.require(Permission.EXECUTION_VIEW);
        boolean all = securityUtils.has(Permission.EXECUTION_VIEW_ALL);
        Long me = all ? null : securityUtils.currentUserId();
        Enums.ExecStatus st = parseStatus(status);
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);

        List<ExecutionFile> rows = new ArrayList<>();
        for (ExecutionFile f : executionFileRepository.findAll()) {
            if (st != null && f.getStatus() != st) continue;
            if (!all && (f.getAssignedLawyer() == null || !f.getAssignedLawyer().getId().equals(me))) continue;
            if (needle != null && !needle.isEmpty() && !matches(f, needle)) continue;
            rows.add(f);
        }
        rows.sort((a, b) -> {
            int c = descNullsLast(a.getOpenedAt(), b.getOpenedAt());
            return c != 0 ? c : descNullsLast(a.getId(), b.getId());
        });

        int p = Math.max(page, 0);
        int s = size <= 0 ? 20 : Math.min(size, 200);
        int from = Math.min(p * s, rows.size());
        int to = Math.min(from + s, rows.size());
        List<ExecutionDtos.ExecutionSummary> items = new ArrayList<>();
        for (ExecutionFile f : rows.subList(from, to)) items.add(summary(f));
        return new PageResult(items, rows.size(), p, s);
    }

    @Transactional
    public ExecutionDtos.ExecutionDetail detail(Long id) {
        ExecutionFile f = get(id);
        requireView(f);
        recomputeCollected(f);

        List<ExecutionOrder> orders = orders(f.getId());
        List<Payment> payments = paymentRepository.findByExecutionFileIdOrderByPaymentDateDesc(f.getId());
        List<Attachment> attachments = storageService.list(EXEC_TYPE, f.getId());

        List<ExecutionDtos.ExecutionOrderDto> orderDtos = new ArrayList<>();
        for (ExecutionOrder o : orders) orderDtos.add(orderDto(o));
        List<ExecutionDtos.ExecutionPaymentDto> paymentDtos = new ArrayList<>();
        for (Payment p : payments) paymentDtos.add(paymentDto(p));
        List<ExecutionDtos.AttachmentDto> attachmentDtos = new ArrayList<>();
        if (attachments != null) for (Attachment a : attachments) attachmentDtos.add(attachmentDto(a));

        return new ExecutionDtos.ExecutionDetail(
                summary(f), orderDtos, paymentDtos, attachmentDtos,
                timeline(f, orders, payments), buildCheck(f), caseBrief(f.getCaseId()),
                securityUtils.has(Permission.EXECUTION_MANAGE));
    }

    // ================================================================= تعديل الملف

    @Transactional
    public ExecutionDtos.ExecutionSummary update(Long id, ExecutionDtos.ExecutionRequest r) {
        ExecutionFile f = get(id);
        requireManage(f);
        requireOpen(f);
        if (r == null) throw new BusinessException("لا توجد بيانات للتعديل");

        StringBuilder changes = new StringBuilder();
        if (r.courtExecutionNumber() != null && !r.courtExecutionNumber().equals(f.getCourtExecutionNumber())) {
            f.setCourtExecutionNumber(blankToNull(r.courtExecutionNumber()));
            changes.append("رقم الملف لدى محكمة التنفيذ، ");
        }
        if (r.court() != null && !r.court().equals(f.getCourt())) {
            f.setCourt(blankToNull(r.court()));
            changes.append("المحكمة، ");
        }
        if (r.expensesAmount() != null) {
            if (r.expensesAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("مبلغ المصروفات لا يجوز أن يكون سالباً");
            }
            if (r.expensesAmount().compareTo(f.getExpensesAmount()) != 0) {
                f.setExpensesAmount(r.expensesAmount());
                changes.append("المصروفات، ");
            }
        }
        if (r.writRecievedAt() != null && !r.writRecievedAt().equals(f.getWritRecievedAt())) {
            f.setWritRecievedAt(r.writRecievedAt());
            changes.append("تاريخ استلام الصيغة التنفيذية، ");
        }
        if (r.notes() != null) {
            f.setNotes(cut(r.notes(), 2000));
            changes.append("الملاحظات، ");
        }
        if (r.assignedLawyerId() != null) {
            User lawyer = userRepository.findById(r.assignedLawyerId())
                    .orElseThrow(() -> new NotFoundException("المحامي المطلوب إسناد الملف إليه غير موجود"));
            if (!lawyer.isActive()) throw new BusinessException("لا يمكن إسناد الملف إلى مستخدم غير مفعّل");
            f.setAssignedLawyer(lawyer);
            changes.append("المحامي المسند (").append(lawyer.getFullName()).append(")، ");
            notificationService.push(lawyer.getId(), Enums.NotificationType.TASK,
                    "إسناد ملف تنفيذ",
                    "أُسند إليك ملف التنفيذ " + f.getExecutionNumber() + " ضد " + f.getDebtor().getName(),
                    EXEC_TYPE, f.getId(), null, "EXE-ASSIGN-" + f.getId() + "-" + lawyer.getId());
        }
        ExecutionFile saved = executionFileRepository.save(f);
        recomputeCollected(saved);

        auditService.log("EXECUTION_UPDATE", ENTITY, saved.getId(), saved.getExecutionNumber(),
                "تعديل بيانات ملف التنفيذ " + saved.getExecutionNumber()
                        + " بتاريخ " + LocalDateTime.now().format(DT) + " بواسطة " + actorName()
                        + " — الحقول المعدّلة: " + (changes.length() == 0 ? "لا شيء" : trimComma(changes.toString())));
        return summary(saved);
    }

    // ================================================================= أوامر التنفيذ

    /**
     * إصدار أمر تنفيذ. يجوز أن يكون أكثر من أمر ساري على نفس الملف في نفس الوقت،
     * فلا يُمنع التعدد ولا يُلغى الأمر السابق تلقائياً.
     */
    @Transactional
    public ExecutionDtos.ExecutionOrderDto addOrder(Long id, ExecutionDtos.OrderRequest r) {
        ExecutionFile f = get(id);
        requireManage(f);
        requireOpen(f);
        if (r == null) throw new BusinessException("بيانات أمر التنفيذ مطلوبة");

        Enums.OrderType type = parseOrderType(r.orderType());
        LocalDate issued = r.issuedDate() == null ? LocalDate.now() : r.issuedDate();
        if (issued.isAfter(LocalDate.now())) {
            throw new BusinessException("تاريخ إصدار أمر التنفيذ لا يجوز أن يكون في المستقبل");
        }
        if (f.getOpenedAt() != null && issued.isBefore(f.getOpenedAt())) {
            throw new BusinessException("تاريخ إصدار أمر التنفيذ لا يجوز أن يسبق تاريخ فتح ملف التنفيذ ("
                    + f.getOpenedAt().format(D) + ")");
        }

        ExecutionOrder o = new ExecutionOrder();
        o.setExecutionFileId(f.getId());
        o.setOrderType(type);
        o.setOrderNumber(blankToNull(r.orderNumber()));
        o.setIssuedDate(issued);
        o.setStatus(Enums.OrderStatus.ACTIVE);
        o.setTargetEntity(cut(r.targetEntity(), 200));
        o.setDetails(cut(r.details(), 2000));
        o.setIssuedBy(currentUserOrNull());
        ExecutionOrder saved = executionOrderRepository.save(o);

        if (f.getStatus() == Enums.ExecStatus.OPEN) {
            f.setStatus(Enums.ExecStatus.IN_PROGRESS);
            executionFileRepository.save(f);
        }

        int active = executionOrderRepository.findByExecutionFileIdAndStatus(f.getId(), Enums.OrderStatus.ACTIVE).size();
        auditService.log("EXECUTION_ORDER_ISSUE", ORDER_ENTITY, saved.getId(),
                f.getExecutionNumber() + "/" + type.name(),
                "إصدار أمر " + type.label() + " على ملف التنفيذ " + f.getExecutionNumber()
                        + " بتاريخ " + issued.format(D) + " بواسطة " + actorName()
                        + (saved.getOrderNumber() != null ? "، رقم الأمر: " + saved.getOrderNumber() : "")
                        + (saved.getTargetEntity() != null ? "، الجهة المخاطَبة: " + saved.getTargetEntity() : "")
                        + " — حالة الأمر: " + Enums.OrderStatus.ACTIVE.label()
                        + "، عدد الأوامر السارية على الملف الآن: " + active + ".");

        if (f.getAssignedLawyer() != null && !f.getAssignedLawyer().getId().equals(securityUtils.currentUserId())) {
            notificationService.push(f.getAssignedLawyer().getId(), Enums.NotificationType.INFO,
                    "أمر تنفيذ جديد",
                    "صدر أمر " + type.label() + " على ملف التنفيذ " + f.getExecutionNumber()
                            + " بتاريخ " + issued.format(D),
                    EXEC_TYPE, f.getId(), null, "EXE-ORDER-" + saved.getId());
        }
        return orderDto(saved);
    }

    /** إلغاء أمر تنفيذ ساري بسبب إلزامي. */
    @Transactional
    public ExecutionDtos.ExecutionOrderDto cancelOrder(Long orderId, String reason) {
        ExecutionOrder o = executionOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("أمر التنفيذ غير موجود"));
        ExecutionFile f = get(o.getExecutionFileId());
        requireManage(f);
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("سبب إلغاء أمر التنفيذ إلزامي");
        }
        if (o.getStatus() == Enums.OrderStatus.CANCELLED) {
            throw new BusinessException("أمر التنفيذ ملغى مسبقاً");
        }
        if (o.getStatus() == Enums.OrderStatus.EXECUTED) {
            throw new BusinessException("أمر التنفيذ نُفِّذ بالفعل — لا يجوز إلغاؤه");
        }

        LocalDateTime now = LocalDateTime.now();
        o.setStatus(Enums.OrderStatus.CANCELLED);
        o.setCancelledAt(now);
        o.setCancelReason(cut(reason.trim(), 500));
        ExecutionOrder saved = executionOrderRepository.save(o);

        auditService.log("EXECUTION_ORDER_CANCEL", ORDER_ENTITY, saved.getId(),
                f.getExecutionNumber() + "/" + saved.getOrderType().name(),
                "إلغاء أمر " + saved.getOrderType().label() + " على ملف التنفيذ " + f.getExecutionNumber()
                        + " بتاريخ " + now.format(DT) + " بواسطة " + actorName()
                        + " — السبب: " + saved.getCancelReason());
        return orderDto(saved);
    }

    // ================================================================= الدفعات على التنفيذ

    /**
     * تسجيل دفعة على ملف التنفيذ. الدفعة تُنشأ على جدول الدفعات نفسه بحقل executionFileId،
     * ولا يُحدَّث المبلغ المحصّل إلا من الدفعات المؤكدة.
     */
    @Transactional
    public ExecutionDtos.ExecutionPaymentDto addPayment(Long id, ExecutionDtos.ExecutionPaymentRequest r) {
        ExecutionFile f = get(id);
        requireManage(f);
        requireOpen(f);
        if (r == null || r.amount() == null) throw new BusinessException("مبلغ الدفعة مطلوب");
        if (r.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("مبلغ الدفعة يجب أن يكون أكبر من صفر");
        }
        LocalDate payDate = r.paymentDate() == null ? LocalDate.now() : r.paymentDate();
        if (payDate.isAfter(LocalDate.now())) {
            throw new BusinessException("تاريخ الدفعة لا يجوز أن يكون في المستقبل");
        }
        Enums.PaymentMethod method = parseMethod(r.method());

        Payment p = new Payment();
        p.setExecutionFileId(f.getId());
        p.setReceiptNumber(numberService.next("RCP", "RCP"));
        p.setAmount(r.amount());
        p.setPaymentDate(payDate);
        p.setMethod(method);
        p.setReferenceNo(blankToNull(r.referenceNo()));
        p.setBankName(blankToNull(r.bankName()));
        p.setPayerName(blankToNull(r.payerName()));
        p.setNotes(cut(r.notes(), 500));
        p.setReceivedBy(currentUserOrNull());

        boolean autoConfirm = securityUtils.has(Permission.PAYMENT_CONFIRM);
        if (autoConfirm) {
            p.setStatus(Enums.PaymentStatus.CONFIRMED);
            p.setConfirmedBy(currentUserOrNull());
            p.setConfirmedAt(LocalDateTime.now());
        } else {
            p.setStatus(Enums.PaymentStatus.PENDING);
        }
        Payment saved = paymentRepository.save(p);

        if (f.getStatus() == Enums.ExecStatus.OPEN) f.setStatus(Enums.ExecStatus.IN_PROGRESS);
        executionFileRepository.save(f);
        recomputeCollected(f);

        auditService.log("EXECUTION_PAYMENT_ADD", "Payment", saved.getId(), saved.getReceiptNumber(),
                "تسجيل دفعة على ملف التنفيذ " + f.getExecutionNumber()
                        + " بمبلغ " + money(saved.getAmount()) + " " + currency()
                        + " (" + method.label() + ") بتاريخ " + payDate.format(D)
                        + " بواسطة " + actorName()
                        + " — إيصال رقم " + saved.getReceiptNumber()
                        + "، حالة الدفعة: " + saved.getStatus().label()
                        + "، المحصّل بعد الدفعة: " + money(f.getCollectedAmount()) + " " + currency()
                        + "، المتبقي: " + money(f.getRemainingAmount()) + " " + currency() + ".");
        return paymentDto(saved);
    }

    // ================================================================= بوابة الاستيفاء

    @Transactional
    public ExecutionDtos.SatisfactionCheck satisfactionCheck(Long id) {
        ExecutionFile f = get(id);
        requireView(f);
        recomputeCollected(f);
        return buildCheck(f);
    }

    /**
     * الاستيفاء الكامل في معاملة واحدة:
     * إعادة فحص الشرط على الخادم، ثم إلغاء كل أمر ساري، ثم إصدار رقم الشهادة،
     * ثم ضبط تواريخ الاستيفاء والإغلاق والحالة، ثم أرشفة الملف، ثم التسجيل في سجل النشاطات.
     */
    @Transactional
    public ExecutionDtos.SatisfyResult satisfy(Long id) {
        ExecutionFile f = get(id);
        requireManage(f);
        if (f.getStatus() == Enums.ExecStatus.CLOSED || f.getCertificateNumber() != null) {
            throw new BusinessException("ملف التنفيذ مغلق باستيفاء سابق — لا يجوز إعادة الاستيفاء أو إصدار شهادة ثانية");
        }

        // (٠) إعادة الفحص على الخادم — لا يُعتمد على ما أرسلته الواجهة
        recomputeCollected(f);
        ExecutionDtos.SatisfactionCheck check = buildCheck(f);
        if (!check.allowed()) {
            throw new BusinessException("لا يمكن إغلاق ملف التنفيذ — لم يكتمل الاستيفاء", check.blockers());
        }

        LocalDateTime now = LocalDateTime.now();

        // (١) إلغاء كل أمر ساري دفعة واحدة
        List<ExecutionOrder> active =
                executionOrderRepository.findByExecutionFileIdAndStatus(f.getId(), Enums.OrderStatus.ACTIVE);
        for (ExecutionOrder o : active) {
            o.setStatus(Enums.OrderStatus.CANCELLED);
            o.setCancelledAt(now);
            o.setCancelReason(SATISFACTION_REASON);
        }
        if (!active.isEmpty()) executionOrderRepository.saveAll(active);

        // (٢) إصدار رقم شهادة الاستيفاء
        String certificateNumber = numberService.next("CERT", "CERT");
        f.setCertificateNumber(certificateNumber);

        // (٣) تواريخ الاستيفاء والإغلاق والحالة: تم الاستيفاء ثم الإغلاق
        f.setSatisfiedAt(now);
        f.setStatus(Enums.ExecStatus.SATISFIED);
        f.setClosedAt(now);
        f.setStatus(Enums.ExecStatus.CLOSED);
        f.setArchived(true);
        ExecutionFile saved = executionFileRepository.save(f);

        // (٤) أرشفة الملف في الأرشيف الشامل
        archiveService.archiveSource(EXEC_TYPE, saved.getId(), saved.getExecutionNumber(),
                Enums.ArchiveType.EXECUTION,
                cut("ملف تنفيذ " + saved.getExecutionNumber() + " — " + saved.getClient().getName()
                        + " ضد " + saved.getDebtor().getName(), 400),
                cut(archiveContent(saved, active.size(), certificateNumber, now), 4000),
                saved.getClient().getName(), saved.getCourt(), now.toLocalDate());

        // (٥) سجل النشاطات بوصف عربي كامل بالوقت والمنفّذ
        auditService.log("EXECUTION_SATISFY", ENTITY, saved.getId(), saved.getExecutionNumber(),
                "اكتمال الاستيفاء وإغلاق ملف التنفيذ " + saved.getExecutionNumber()
                        + " بتاريخ " + now.format(DT) + " بواسطة " + actorName()
                        + " — المدين: " + saved.getDebtor().getName()
                        + "، صاحب الحق: " + saved.getClient().getName()
                        + "، أصل الحكم: " + money(saved.getJudgmentAmount()) + " " + currency()
                        + "، المصروفات: " + money(saved.getExpensesAmount()) + " " + currency()
                        + "، إجمالي المستحق: " + money(total(saved)) + " " + currency()
                        + "، المحصّل: " + money(saved.getCollectedAmount()) + " " + currency()
                        + "، المتبقي: " + money(saved.getRemainingAmount()) + " " + currency()
                        + " — أُلغيت " + active.size() + " من أوامر التنفيذ السارية بسبب «" + SATISFACTION_REASON + "»"
                        + "، وصدرت شهادة استيفاء رقم " + certificateNumber
                        + "، وأُرشف الملف في الأرشيف الشامل.");

        if (saved.getAssignedLawyer() != null) {
            notificationService.push(saved.getAssignedLawyer().getId(), Enums.NotificationType.INFO,
                    "اكتمال الاستيفاء",
                    "أُغلق ملف التنفيذ " + saved.getExecutionNumber()
                            + " باستيفاء كامل وصدرت شهادة الاستيفاء رقم " + certificateNumber,
                    EXEC_TYPE, saved.getId(), null, "EXE-SATISFY-" + saved.getId());
        }

        return new ExecutionDtos.SatisfyResult(certificateNumber, saved.getId(), saved.getExecutionNumber(),
                now, active.size(),
                "اكتمل الاستيفاء وأُغلق ملف التنفيذ " + saved.getExecutionNumber()
                        + " — شهادة الاستيفاء رقم " + certificateNumber);
    }

    /** شهادة الاستيفاء المرقمة — لا تُصدر إلا لملف مستوفى ومغلق. */
    @Transactional(readOnly = true)
    public ExecutionDtos.CertificateDto certificate(Long id) {
        ExecutionFile f = get(id);
        requireView(f);
        if (f.getSatisfiedAt() == null || f.getCertificateNumber() == null) {
            throw new BusinessException("لم يكتمل استيفاء هذا الملف بعد — لا يمكن إصدار شهادة الاستيفاء قبل تحصيل كامل المبلغ وإغلاق الملف");
        }
        com.qanoon.domain.LegalCase c = f.getCaseId() == null ? null
                : legalCaseRepository.findById(f.getCaseId()).orElse(null);
        Party client = f.getClient();
        Party debtor = f.getDebtor();
        BigDecimal totalAmount = total(f);

        String statement = "تشهد " + officeName() + " بأن المدين " + debtor.getName()
                + " قد أوفى بكامل المبلغ المحكوم به وقدره " + money(totalAmount) + " " + currency()
                + " لصالح " + client.getName()
                + " في ملف التنفيذ رقم " + f.getExecutionNumber()
                + (f.getCourtExecutionNumber() != null ? " (رقم الملف لدى المحكمة: " + f.getCourtExecutionNumber() + ")" : "")
                + (c != null && c.getJudgmentNumber() != null ? " تنفيذاً للحكم رقم " + c.getJudgmentNumber() : "")
                + "، وأن الملف أُغلق باستيفاء كامل بتاريخ " + f.getSatisfiedAt().format(D) + ".";

        return new ExecutionDtos.CertificateDto(
                f.getCertificateNumber(),
                officeName(),
                f.getId(),
                f.getExecutionNumber(),
                f.getCourtExecutionNumber(),
                f.getCourt(),
                f.getCaseId(),
                c == null ? null : c.getCaseNumber(),
                c == null ? null : c.getJudgmentNumber(),
                c == null ? null : c.getJudgmentDate(),
                client.getName(),
                client.getIdNumber(),
                debtor.getName(),
                debtor.getIdNumber(),
                f.getJudgmentAmount(),
                f.getExpensesAmount(),
                totalAmount,
                f.getCollectedAmount(),
                currency(),
                f.getSatisfiedAt().toLocalDate(),
                f.getSatisfiedAt(),
                LocalDate.now(),
                actorName(),
                statement);
    }

    // ================================================================= داخلي

    private ExecutionFile get(Long id) {
        if (id == null) throw new NotFoundException("ملف التنفيذ غير موجود");
        return executionFileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ملف التنفيذ غير موجود"));
    }

    private void requireView(ExecutionFile f) {
        if (securityUtils.has(Permission.EXECUTION_VIEW_ALL)) return;
        securityUtils.require(Permission.EXECUTION_VIEW);
        Long me = securityUtils.currentUserId();
        if (f.getAssignedLawyer() == null || me == null || !f.getAssignedLawyer().getId().equals(me)) {
            throw new ForbiddenException("لا تملك صلاحية الاطلاع على ملف التنفيذ " + f.getExecutionNumber()
                    + " — الملف غير مسند إليك");
        }
    }

    private void requireManage(ExecutionFile f) {
        securityUtils.require(Permission.EXECUTION_MANAGE);
        requireView(f);
    }

    private void requireOpen(ExecutionFile f) {
        if (f.getStatus() == Enums.ExecStatus.CLOSED || f.getSatisfiedAt() != null) {
            throw new BusinessException("ملف التنفيذ " + f.getExecutionNumber()
                    + " مغلق باستيفاء كامل — ما أُغلق لا يُكتب فوقه");
        }
    }

    /** المبلغ المحصّل يُشتق من الدفعات المؤكدة وحدها. */
    private void recomputeCollected(ExecutionFile f) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Payment p : paymentRepository.findByExecutionFileIdOrderByPaymentDateDesc(f.getId())) {
            if (p.getStatus() == Enums.PaymentStatus.CONFIRMED && p.getAmount() != null) {
                sum = sum.add(p.getAmount());
            }
        }
        if (f.getCollectedAmount() == null || f.getCollectedAmount().compareTo(sum) != 0) {
            f.setCollectedAmount(sum);
            executionFileRepository.save(f);
        }
    }

    /** الشرط الوحيد للسماح بالإغلاق: المتبقي صفر. الأوامر السارية تُعرض ولا تمنع. */
    private ExecutionDtos.SatisfactionCheck buildCheck(ExecutionFile f) {
        BigDecimal remaining = f.getRemainingAmount();
        int activeOrders =
                executionOrderRepository.findByExecutionFileIdAndStatus(f.getId(), Enums.OrderStatus.ACTIVE).size();
        List<String> blockers = new ArrayList<>();
        boolean allowed = remaining.compareTo(BigDecimal.ZERO) == 0;
        if (!allowed) {
            blockers.add("لا يمكن الإغلاق — المتبقي " + money(remaining) + " درهم");
        }
        if (f.getStatus() == Enums.ExecStatus.CLOSED || f.getCertificateNumber() != null) {
            allowed = false;
            blockers.add("ملف التنفيذ مغلق باستيفاء سابق — شهادة الاستيفاء رقم " + nz(f.getCertificateNumber()));
        }
        return new ExecutionDtos.SatisfactionCheck(allowed, remaining, activeOrders, blockers);
    }

    private List<ExecutionOrder> orders(Long fileId) {
        List<ExecutionOrder> list = new ArrayList<>(executionOrderRepository.findByExecutionFileId(fileId));
        list.sort((a, b) -> {
            int c = descNullsLast(a.getIssuedDate(), b.getIssuedDate());
            return c != 0 ? c : descNullsLast(a.getId(), b.getId());
        });
        return list;
    }

    /** الخط الزمني للملف: كل أمر بتاريخه وحالته، مع الدفعات ومحطات الملف. */
    private List<ExecutionDtos.TimelineEntry> timeline(ExecutionFile f, List<ExecutionOrder> orders, List<Payment> payments) {
        List<ExecutionDtos.TimelineEntry> t = new ArrayList<>();

        t.add(new ExecutionDtos.TimelineEntry(
                f.getOpenedAt() == null ? f.getCreatedAt() : f.getOpenedAt().atStartOfDay(),
                "FILE_OPEN", "فتح الملف",
                "فتح ملف التنفيذ " + f.getExecutionNumber(),
                "مبلغ الحكم " + money(f.getJudgmentAmount()) + " " + currency()
                        + " — المدين: " + f.getDebtor().getName(),
                Enums.ExecStatus.OPEN.name(), Enums.ExecStatus.OPEN.label(), f.getId()));

        if (f.getWritRecievedAt() != null) {
            t.add(new ExecutionDtos.TimelineEntry(f.getWritRecievedAt().atStartOfDay(),
                    "WRIT", "الصيغة التنفيذية",
                    "استلام الصيغة التنفيذية",
                    "بتاريخ " + f.getWritRecievedAt().format(D),
                    null, null, f.getId()));
        }

        for (ExecutionOrder o : orders) {
            t.add(new ExecutionDtos.TimelineEntry(
                    o.getIssuedDate().atStartOfDay(),
                    "ORDER", "أمر تنفيذ",
                    "إصدار أمر " + o.getOrderType().label(),
                    (o.getOrderNumber() != null ? "رقم الأمر " + o.getOrderNumber() + " — " : "")
                            + (o.getTargetEntity() != null ? "الجهة المخاطَبة: " + o.getTargetEntity() : "")
                            + (o.getDetails() != null ? (o.getTargetEntity() != null ? " — " : "") + o.getDetails() : ""),
                    o.getStatus().name(), o.getStatus().label(), o.getId()));
            if (o.getCancelledAt() != null) {
                t.add(new ExecutionDtos.TimelineEntry(o.getCancelledAt(),
                        "ORDER_CANCEL", "إلغاء أمر",
                        "إلغاء أمر " + o.getOrderType().label(),
                        "السبب: " + nz(o.getCancelReason()),
                        Enums.OrderStatus.CANCELLED.name(), Enums.OrderStatus.CANCELLED.label(), o.getId()));
            }
        }

        for (Payment p : payments) {
            t.add(new ExecutionDtos.TimelineEntry(
                    p.getPaymentDate().atStartOfDay(),
                    "PAYMENT", "دفعة",
                    "دفعة بمبلغ " + money(p.getAmount()) + " " + currency(),
                    "إيصال " + p.getReceiptNumber() + " — " + p.getMethod().label()
                            + (p.getPayerName() != null ? " — الدافع: " + p.getPayerName() : ""),
                    p.getStatus().name(), p.getStatus().label(), p.getId()));
        }

        if (f.getSatisfiedAt() != null) {
            t.add(new ExecutionDtos.TimelineEntry(f.getSatisfiedAt(),
                    "SATISFIED", "استيفاء",
                    "اكتمال الاستيفاء وإغلاق الملف",
                    "شهادة استيفاء رقم " + nz(f.getCertificateNumber())
                            + " — المحصّل " + money(f.getCollectedAmount()) + " " + currency(),
                    Enums.ExecStatus.CLOSED.name(), Enums.ExecStatus.CLOSED.label(), f.getId()));
        }

        t.sort((a, b) -> descNullsLast(a.at(), b.at()));
        return t;
    }

    private ExecutionDtos.ExecutionSummary summary(ExecutionFile f) {
        List<ExecutionOrder> all = executionOrderRepository.findByExecutionFileId(f.getId());
        int active = 0;
        for (ExecutionOrder o : all) if (o.getStatus() == Enums.OrderStatus.ACTIVE) active++;
        com.qanoon.domain.LegalCase c = f.getCaseId() == null ? null
                : legalCaseRepository.findById(f.getCaseId()).orElse(null);
        return new ExecutionDtos.ExecutionSummary(
                f.getId(),
                f.getExecutionNumber(),
                f.getCourtExecutionNumber(),
                f.getCaseId(),
                c == null ? null : c.getCaseNumber(),
                f.getClient() == null ? null : f.getClient().getName(),
                f.getDebtor() == null ? null : f.getDebtor().getName(),
                f.getAssignedLawyer() == null ? null : f.getAssignedLawyer().getId(),
                f.getAssignedLawyer() == null ? null : f.getAssignedLawyer().getFullName(),
                f.getCourt(),
                f.getJudgmentAmount(),
                f.getExpensesAmount(),
                f.getCollectedAmount(),
                f.getRemainingAmount(),
                total(f),
                f.getStatus().name(),
                f.getStatus().label(),
                f.getOpenedAt(),
                f.getWritRecievedAt(),
                f.getSatisfiedAt(),
                f.getCertificateNumber(),
                f.getClosedAt(),
                f.isArchived(),
                f.getNotes(),
                active,
                all.size());
    }

    private ExecutionDtos.CaseBrief caseBrief(Long caseId) {
        if (caseId == null) return null;
        LegalCase c = legalCaseRepository.findById(caseId).orElse(null);
        if (c == null) return null;
        return new ExecutionDtos.CaseBrief(
                c.getId(), c.getCaseNumber(), c.getCourtCaseNumber(), c.getCourt(),
                c.getCaseType() == null ? null : c.getCaseType().name(),
                c.getCaseType() == null ? null : c.getCaseType().label(),
                c.getSubject(),
                c.getStatus() == null ? null : c.getStatus().name(),
                c.getStatus() == null ? null : c.getStatus().label(),
                c.getJudgmentDate(), c.getJudgmentNumber(),
                c.getJudgmentFor() == null ? null : c.getJudgmentFor().name(),
                c.getJudgmentFor() == null ? null : c.getJudgmentFor().label(),
                c.getJudgmentAmount(), c.getJudgmentSummary(), c.isJudgmentFinal(),
                c.getAssignedLawyer() == null ? null : c.getAssignedLawyer().getFullName());
    }

    private ExecutionDtos.ExecutionOrderDto orderDto(ExecutionOrder o) {
        return new ExecutionDtos.ExecutionOrderDto(
                o.getId(), o.getExecutionFileId(),
                o.getOrderType().name(), o.getOrderType().label(),
                o.getOrderNumber(), o.getIssuedDate(),
                o.getStatus().name(), o.getStatus().label(),
                o.getTargetEntity(), o.getDetails(),
                o.getCancelledAt(), o.getCancelReason(),
                o.getIssuedBy() == null ? null : o.getIssuedBy().getFullName(),
                o.getStatus() == Enums.OrderStatus.ACTIVE);
    }

    private ExecutionDtos.ExecutionPaymentDto paymentDto(Payment p) {
        return new ExecutionDtos.ExecutionPaymentDto(
                p.getId(), p.getReceiptNumber(), p.getAmount(), p.getPaymentDate(),
                p.getMethod().name(), p.getMethod().label(),
                p.getStatus().name(), p.getStatus().label(),
                p.getReferenceNo(), p.getBankName(), p.getPayerName(), p.getNotes(),
                p.getReceivedBy() == null ? null : p.getReceivedBy().getFullName(),
                p.getConfirmedBy() == null ? null : p.getConfirmedBy().getFullName(),
                p.getConfirmedAt());
    }

    private ExecutionDtos.AttachmentDto attachmentDto(Attachment a) {
        return new ExecutionDtos.AttachmentDto(a.getId(), a.getFileName(), a.getContentType(),
                a.getFileSize(), a.getCategory(), a.getDescription(), a.getCreatedAt());
    }

    private String archiveContent(ExecutionFile f, int cancelledOrders, String certificateNumber, LocalDateTime at) {
        StringBuilder sb = new StringBuilder();
        sb.append("ملف تنفيذ رقم ").append(f.getExecutionNumber());
        if (f.getCourtExecutionNumber() != null) sb.append(" — لدى المحكمة: ").append(f.getCourtExecutionNumber());
        sb.append("\nالمحكمة: ").append(nz(f.getCourt()));
        sb.append("\nصاحب الحق: ").append(f.getClient().getName());
        sb.append("\nالمدين: ").append(f.getDebtor().getName());
        sb.append("\nأصل الحكم: ").append(money(f.getJudgmentAmount())).append(" ").append(currency());
        sb.append("\nالمصروفات: ").append(money(f.getExpensesAmount())).append(" ").append(currency());
        sb.append("\nإجمالي المستحق: ").append(money(total(f))).append(" ").append(currency());
        sb.append("\nالمحصّل: ").append(money(f.getCollectedAmount())).append(" ").append(currency());
        sb.append("\nالمتبقي: ").append(money(f.getRemainingAmount())).append(" ").append(currency());
        sb.append("\nتاريخ فتح الملف: ").append(f.getOpenedAt() == null ? "-" : f.getOpenedAt().format(D));
        if (f.getWritRecievedAt() != null) {
            sb.append("\nتاريخ استلام الصيغة التنفيذية: ").append(f.getWritRecievedAt().format(D));
        }
        sb.append("\nتاريخ الاستيفاء: ").append(at.format(DT));
        sb.append("\nشهادة الاستيفاء رقم: ").append(certificateNumber);
        sb.append("\nأوامر التنفيذ الملغاة عند الاستيفاء: ").append(cancelledOrders);
        List<ExecutionOrder> all = executionOrderRepository.findByExecutionFileId(f.getId());
        if (!all.isEmpty()) {
            sb.append("\nسجل أوامر التنفيذ:");
            for (ExecutionOrder o : all) {
                sb.append("\n  • ").append(o.getOrderType().label())
                        .append(" بتاريخ ").append(o.getIssuedDate().format(D))
                        .append(" — الحالة: ").append(o.getStatus().label());
                if (o.getTargetEntity() != null) sb.append(" — الجهة: ").append(o.getTargetEntity());
                if (o.getCancelReason() != null) sb.append(" — سبب الإلغاء: ").append(o.getCancelReason());
            }
        }
        if (f.getNotes() != null && !f.getNotes().isBlank()) sb.append("\nملاحظات: ").append(f.getNotes());
        sb.append("\nأُنجز الاستيفاء بواسطة: ").append(actorName());
        return sb.toString();
    }

    private boolean matches(ExecutionFile f, String needle) {
        return contains(f.getExecutionNumber(), needle)
                || contains(f.getCourtExecutionNumber(), needle)
                || contains(f.getCourt(), needle)
                || contains(f.getCertificateNumber(), needle)
                || contains(f.getNotes(), needle)
                || (f.getClient() != null && contains(f.getClient().getName(), needle))
                || (f.getDebtor() != null && contains(f.getDebtor().getName(), needle))
                || (f.getAssignedLawyer() != null && contains(f.getAssignedLawyer().getFullName(), needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static BigDecimal total(ExecutionFile f) {
        BigDecimal j = f.getJudgmentAmount() == null ? BigDecimal.ZERO : f.getJudgmentAmount();
        BigDecimal e = f.getExpensesAmount() == null ? BigDecimal.ZERO : f.getExpensesAmount();
        return j.add(e);
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        if (a != null) return a;
        if (b != null) return b;
        return BigDecimal.ZERO;
    }

    private static Enums.ExecStatus parseStatus(String v) {
        return EnumParser.optional(Enums.ExecStatus.class, v, "حالة ملف التنفيذ");
    }

    /** مطابقة حرفية عمداً: إصدار أمر تنفيذ إجراء قضائي لا يُخمَّن رمزه. */
    private static Enums.OrderType parseOrderType(String v) {
        return EnumParser.requiredExact(Enums.OrderType.class, v,
                "نوع أمر التنفيذ", "نوع أمر التنفيذ مطلوب");
    }

    /** مطابقة حرفية عمداً: تسجيل دفعة على ملف التنفيذ يغيّر المبلغ المحصّل. */
    private static Enums.PaymentMethod parseMethod(String v) {
        return EnumParser.requiredExact(Enums.PaymentMethod.class, v,
                "طريقة الدفع", "طريقة الدفع مطلوبة");
    }

    private static String money(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return df.format(x);
    }

    private String currency() {
        String c = settingService.currency();
        return c == null || c.isBlank() ? "درهم" : c;
    }

    private String officeName() {
        String n = settingService.get("officeName", "مكتب المحاماة");
        return n == null || n.isBlank() ? "مكتب المحاماة" : n;
    }

    private User currentUserOrNull() {
        try {
            return securityUtils.currentUser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String actorName() {
        User u = currentUserOrNull();
        if (u != null && u.getFullName() != null) return u.getFullName();
        String username = CurrentUserHolder.username();
        return username == null || username.isBlank() ? "النظام" : username;
    }

    private static String nz(String v) {
        return v == null ? "-" : v;
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String cut(String v, int max) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String trimComma(String v) {
        String t = v.trim();
        while (t.endsWith("،") || t.endsWith(",")) t = t.substring(0, t.length() - 1).trim();
        return t;
    }

    /** مقارنة تنازلية تضع القيم الفارغة في آخر القائمة. */
    private static <T extends Comparable<T>> int descNullsLast(T a, T b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }
}
