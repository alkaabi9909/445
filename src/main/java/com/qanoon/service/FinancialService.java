package com.qanoon.service;

import com.qanoon.common.AuditService;
import com.qanoon.common.BusinessException;
import com.qanoon.common.EnumParser;
import com.qanoon.common.ForbiddenException;
import com.qanoon.common.NotFoundException;
import com.qanoon.common.NumberService;
import com.qanoon.common.PageResult;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.StorageService;
import com.qanoon.domain.Attachment;
import com.qanoon.domain.Communication;
import com.qanoon.domain.Enums;
import com.qanoon.domain.FinancialFile;
import com.qanoon.domain.Installment;
import com.qanoon.domain.LegalCase;
import com.qanoon.domain.Party;
import com.qanoon.domain.Payment;
import com.qanoon.domain.PaymentPlan;
import com.qanoon.domain.Permission;
import com.qanoon.domain.User;
import com.qanoon.dto.FinancialDtos;
import com.qanoon.repo.AttachmentRepository;
import com.qanoon.repo.CommunicationRepository;
import com.qanoon.repo.FinancialFileRepository;
import com.qanoon.repo.InstallmentRepository;
import com.qanoon.repo.LegalCaseRepository;
import com.qanoon.repo.PartyRepository;
import com.qanoon.repo.PaymentPlanRepository;
import com.qanoon.repo.PaymentRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * المسار الأول: الملف المالي.
 * القاعدة الذهبية: «المالي يقود الحالة» — حالة الملف مشتقة من الدفعات المؤكدة فقط،
 * ولا تُضبط يدوياً إلا عبر وجهات الإنهاء الخمس.
 */
@Service
@RequiredArgsConstructor
public class FinancialService {

    /** نوع الكيان المستخدم في المرفقات والأرشيف. */
    public static final String ENTITY_TYPE = "FINANCIAL_FILE";
    /** نوع كيان القضية — يُستخدم عند التصعيد. */
    public static final String CASE_ENTITY_TYPE = "CASE";
    /** فئة مستند المطالبة المالية الإلزامي لفتح الملف. */
    public static final String CLAIM_CATEGORY = "مطالبة";
    /** فئة مستند الإعفاء. */
    public static final String EXEMPTION_CATEGORY = "إعفاء";

    private final FinancialFileRepository fileRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentPlanRepository planRepo;
    private final InstallmentRepository installmentRepo;
    private final CommunicationRepository commRepo;
    private final AttachmentRepository attachmentRepo;
    private final PartyRepository partyRepo;
    private final UserRepository userRepo;
    private final LegalCaseRepository caseRepo;
    private final NumberService numberService;
    private final StorageService storageService;
    private final ArchiveService archiveService;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    // ==================================================================
    // القراءة
    // ==================================================================

    /** قائمة الملفات المالية مع تصفية بالحالة والبحث النصي وترقيم الصفحات. */
    @Transactional(readOnly = true)
    public PageResult list(String status, String q, int page, int size) {
        requireViewPermission();
        Enums.FileStatus filter = EnumParser.optional(Enums.FileStatus.class, status, "حالة الملف");
        boolean viewAll = securityUtils.has(Permission.FINANCIAL_VIEW_ALL);

        List<FinancialFile> base;
        if (!viewAll) {
            base = fileRepo.findByAssignedLawyer_Id(securityUtils.currentUserId());
        } else if (filter != null) {
            base = fileRepo.findByStatus(filter);
        } else {
            base = fileRepo.findAll();
        }

        String needle = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);
        List<FinancialFile> matched = new ArrayList<>();
        for (FinancialFile f : base) {
            if (filter != null && f.getStatus() != filter) {
                continue;
            }
            if (needle != null && !matches(f, needle)) {
                continue;
            }
            matched.add(f);
        }
        matched.sort(Comparator
                .comparing(FinancialFile::getOpenedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FinancialFile::getId, Comparator.nullsLast(Comparator.reverseOrder())));

        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, matched.size());
        int to = Math.min(from + safeSize, matched.size());

        List<FinancialDtos.FinancialFileRow> rows = new ArrayList<>();
        for (FinancialFile f : matched.subList(from, to)) {
            rows.add(toRow(f));
        }
        return new PageResult(rows, matched.size(), safePage, safeSize);
    }

    /** التفاصيل الكاملة: الملف + الدفعات + الخطة + الأقساط + سجل التواصل + المرفقات + المتبقي. */
    @Transactional(readOnly = true)
    public FinancialDtos.FinancialFileDetail detail(Long id) {
        FinancialFile file = getOrThrow(id);
        checkView(file);

        Map<Long, Integer> seqByInstallment = new HashMap<>();
        for (Installment inst : installmentRepo.findByFinancialFileIdOrderBySeqAsc(id)) {
            seqByInstallment.put(inst.getId(), inst.getSeq());
        }

        List<FinancialDtos.PaymentRow> payments = new ArrayList<>();
        for (Payment p : paymentRepo.findByFinancialFileIdOrderByPaymentDateDesc(id)) {
            payments.add(toRow(p, p.getInstallmentId() == null ? null : seqByInstallment.get(p.getInstallmentId())));
        }

        PaymentPlan plan = planRepo.findFirstByFinancialFileIdAndActiveTrue(id).orElse(null);
        List<Installment> installments;
        if (plan != null) {
            installments = new ArrayList<>(plan.getInstallments());
            installments.sort(Comparator.comparingInt(Installment::getSeq));
        } else {
            installments = installmentRepo.findByFinancialFileIdOrderBySeqAsc(id);
        }
        List<FinancialDtos.InstallmentRow> installmentRows = new ArrayList<>();
        for (Installment inst : installments) {
            installmentRows.add(toRow(inst));
        }

        List<FinancialDtos.CommunicationRow> comms = new ArrayList<>();
        for (Communication c : commRepo.findByFinancialFileIdOrderByCommDateDesc(id)) {
            comms.add(toRow(c));
        }

        List<FinancialDtos.AttachmentRow> attachments = new ArrayList<>();
        for (Attachment a : attachmentRepo.findByEntityTypeAndEntityId(ENTITY_TYPE, id)) {
            attachments.add(toRow(a));
        }

        return new FinancialDtos.FinancialFileDetail(
                toRow(file),
                payments,
                plan == null ? null : toRow(plan, installments),
                installmentRows,
                comms,
                attachments,
                file.getRemainingAmount(),
                file.getClosedAt() == null,
                paymentsAllowed(file));
    }

    /** يجلب الملف أو يرمي رسالة عربية. */
    @Transactional(readOnly = true)
    public FinancialFile getOrThrow(Long id) {
        if (id == null) {
            throw new NotFoundException("رقم الملف المالي غير محدد");
        }
        return fileRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("الملف المالي المطلوب غير موجود"));
    }

    // ==================================================================
    // الإنشاء والتعديل
    // ==================================================================

    /**
     * فتح ملف مالي جديد. يشترط إرفاق مستند مطالبة مالية واحد على الأقل
     * ضمن attachmentIds (مرفوع مسبقاً عبر خدمة المرفقات).
     */
    @Transactional
    public FinancialFile create(FinancialDtos.FinancialFileRequest req) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        if (req == null) {
            throw new BusinessException("بيانات الملف المالي غير مكتملة");
        }

        List<Attachment> claimDocs = loadAttachments(req.attachmentIds());
        boolean hasClaim = claimDocs.stream().anyMatch(a -> isCategory(a.getCategory(), CLAIM_CATEGORY));
        if (!hasClaim) {
            throw new BusinessException("لا يمكن فتح الملف المالي دون إرفاق مستند المطالبة المالية");
        }

        FinancialFile file = new FinancialFile();
        file.setFileNumber(numberService.next("FIN", "FIN"));
        applyRequest(file, req);
        file.setStatus(Enums.FileStatus.OPEN);
        file.setPaidAmount(BigDecimal.ZERO);
        file.setExemptedAmount(BigDecimal.ZERO);
        file.setOpenedAt(req.openedAt() == null ? LocalDate.now() : req.openedAt());
        fileRepo.save(file);

        linkAttachments(claimDocs, file.getId());
        recomputeStatus(file);

        auditService.log("FINANCIAL_CREATE", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                "فتح ملف مالي جديد للموكل " + safeName(file.getClient())
                        + " ضد " + safeName(file.getDebtor())
                        + " بمبلغ مطالبة " + money(file.getClaimAmount()) + " درهم");
        return file;
    }

    /** تعديل بيانات الملف. مرفوض على أي ملف مغلق. */
    @Transactional
    public FinancialFile update(Long id, FinancialDtos.FinancialFileRequest req) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        FinancialFile file = getOrThrow(id);
        checkView(file);
        ensureNotClosed(file);
        if (req == null) {
            throw new BusinessException("بيانات الملف المالي غير مكتملة");
        }

        applyRequest(file, req);
        if (req.openedAt() != null) {
            file.setOpenedAt(req.openedAt());
        }
        fileRepo.save(file);

        if (req.attachmentIds() != null && !req.attachmentIds().isEmpty()) {
            linkAttachments(loadAttachments(req.attachmentIds()), file.getId());
        }
        recomputeStatus(file);

        auditService.log("FINANCIAL_UPDATE", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                "تعديل بيانات الملف المالي — مبلغ المطالبة " + money(file.getClaimAmount()) + " درهم");
        return file;
    }

    /** تسجيل تواصل أو إنذار على الملف، ثم إعادة اشتقاق الحالة. */
    @Transactional
    public Communication addCommunication(Long id, FinancialDtos.CommunicationRequest req) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        FinancialFile file = getOrThrow(id);
        checkView(file);
        ensureNotClosed(file);
        if (req == null || req.summary() == null || req.summary().isBlank()) {
            throw new BusinessException("ملخص التواصل إلزامي");
        }
        // مسار كتابة: مطابقة حرفية لرمز نوع التواصل
        Enums.CommType type = EnumParser.requiredExact(Enums.CommType.class, req.type(),
                "نوع التواصل", "نوع التواصل إلزامي");

        Communication comm = new Communication();
        comm.setFinancialFileId(file.getId());
        comm.setType(type);
        comm.setCommDate(req.commDate() == null ? LocalDateTime.now() : req.commDate());
        comm.setSummary(req.summary().trim());
        comm.setOutcome(trimToNull(req.outcome()));
        comm.setContactPerson(trimToNull(req.contactPerson()));
        comm.setReferenceNo(trimToNull(req.referenceNo()));
        commRepo.save(comm);

        recomputeStatus(file);

        auditService.log("FINANCIAL_COMMUNICATION", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                "تسجيل " + type.label() + " على الملف المالي — " + comm.getSummary());
        return comm;
    }

    // ==================================================================
    // خطة التقسيط
    // ==================================================================

    /**
     * إنشاء خطة تقسيط جديدة: تُلغي الخطة النشطة السابقة، وتولّد الأقساط بمبلغ متساوٍ
     * مع تسوية الكسر في القسط الأخير، وتواريخ استحقاق startDate + n × intervalMonths.
     */
    @Transactional
    public PaymentPlan createPlan(Long id, FinancialDtos.PaymentPlanRequest req) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        FinancialFile file = getOrThrow(id);
        checkView(file);
        ensureNotClosed(file);
        if (req == null) {
            throw new BusinessException("بيانات خطة التقسيط غير مكتملة");
        }

        BigDecimal total = req.totalAmount();
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("إجمالي مبلغ خطة التقسيط يجب أن يكون أكبر من صفر");
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        if (req.installmentsCount() == null || req.installmentsCount() < 1) {
            throw new BusinessException("عدد الأقساط يجب أن يكون قسطاً واحداً على الأقل");
        }
        if (req.installmentsCount() > 240) {
            throw new BusinessException("عدد الأقساط يتجاوز الحد المسموح (240 قسطاً)");
        }
        if (req.startDate() == null) {
            throw new BusinessException("تاريخ بداية التقسيط إلزامي");
        }
        int interval = req.intervalMonths() == null || req.intervalMonths() < 1 ? 1 : req.intervalMonths();
        int count = req.installmentsCount();

        // إلغاء الخطة النشطة السابقة وأقساطها غير المسددة
        Optional<PaymentPlan> previous = planRepo.findFirstByFinancialFileIdAndActiveTrue(file.getId());
        if (previous.isPresent()) {
            PaymentPlan old = previous.get();
            old.setActive(false);
            planRepo.save(old);
            for (Installment inst : installmentRepo.findByFinancialFileIdOrderBySeqAsc(file.getId())) {
                if (inst.getPlan() != null && old.getId().equals(inst.getPlan().getId())
                        && inst.getStatus() != Enums.InstallmentStatus.PAID
                        && inst.getStatus() != Enums.InstallmentStatus.CANCELLED) {
                    inst.setStatus(Enums.InstallmentStatus.CANCELLED);
                    inst.setCancelReason("أُلغي تلقائياً باعتماد خطة تقسيط جديدة");
                    installmentRepo.save(inst);
                }
            }
            auditService.log("PLAN_SUPERSEDE", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                    "إلغاء خطة التقسيط السابقة رقم " + old.getId() + " باعتماد خطة جديدة");
        }

        PaymentPlan plan = new PaymentPlan();
        plan.setFinancialFileId(file.getId());
        plan.setTotalAmount(total);
        plan.setInstallmentsCount(count);
        plan.setStartDate(req.startDate());
        plan.setIntervalMonths(interval);
        // مسار كتابة: الحقل اختياري لكن مطابقته حرفية
        plan.setPaymentMethod(EnumParser.optionalExact(
                Enums.PaymentMethod.class, req.paymentMethod(), "طريقة السداد"));
        plan.setNotes(trimToNull(req.notes()));
        plan.setActive(true);
        planRepo.save(plan);

        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal allocated = each.multiply(BigDecimal.valueOf(count - 1L)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal last = total.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now();

        for (int n = 0; n < count; n++) {
            Installment inst = new Installment();
            inst.setPlan(plan);
            inst.setFinancialFileId(file.getId());
            inst.setSeq(n + 1);
            inst.setDueDate(req.startDate().plusMonths((long) n * interval));
            inst.setAmount(n == count - 1 ? last : each);
            inst.setPaidAmount(BigDecimal.ZERO);
            inst.setStatus(inst.getDueDate().isBefore(today)
                    ? Enums.InstallmentStatus.OVERDUE
                    : Enums.InstallmentStatus.DUE);
            installmentRepo.save(inst);
        }

        recomputeStatus(file);

        auditService.log("PLAN_CREATE", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                "اعتماد خطة تقسيط: " + count + " قسطاً بإجمالي " + money(total)
                        + " درهم، تبدأ في " + req.startDate() + " وبفاصل " + interval + " شهر");
        return plan;
    }

    // ==================================================================
    // اشتقاق الحالة — المالي يقود الحالة
    // ==================================================================

    /**
     * يعيد حساب المحصّل وحالة الملف من الدفعات المؤكدة فقط.
     * يُستدعى بعد كل تأكيد/ارتجاع/إلغاء دفعة، وبعد إنشاء خطة تقسيط، وبعد تسجيل تواصل.
     */
    @Transactional
    public void recomputeStatus(FinancialFile file) {
        if (file == null || file.getId() == null) {
            return;
        }
        BigDecimal paid = paymentRepo
                .findByFinancialFileIdAndStatus(file.getId(), Enums.PaymentStatus.CONFIRMED)
                .stream()
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        file.setPaidAmount(paid);

        if (file.getClosureType() == null) {
            BigDecimal claim = nz(file.getClaimAmount());
            Enums.FileStatus next;
            if (claim.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(claim) >= 0) {
                next = Enums.FileStatus.PAID;
            } else if (planRepo.findFirstByFinancialFileIdAndActiveTrue(file.getId()).isPresent()) {
                next = Enums.FileStatus.INSTALLMENT;
            } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
                next = Enums.FileStatus.PARTIALLY_PAID;
            } else {
                List<Communication> comms = commRepo.findByFinancialFileIdOrderByCommDateDesc(file.getId());
                if (comms.stream().anyMatch(c -> c.getType() == Enums.CommType.WARNING)) {
                    next = Enums.FileStatus.WARNED;
                } else if (!comms.isEmpty()) {
                    next = Enums.FileStatus.CONTACTED;
                } else {
                    next = Enums.FileStatus.OPEN;
                }
            }
            file.setStatus(next);
        }
        fileRepo.save(file);
    }

    // ==================================================================
    // الإنهاء — الوجهات الخمس
    // ==================================================================

    /**
     * إنهاء الملف المالي بإحدى الوجهات الخمس.
     * التصعيد يعيد رقم القضية المستحدثة؛ باقي الوجهات تؤرشف الملف.
     */
    @Transactional
    public FinancialDtos.CloseResult close(Long id, Enums.ClosureType closureType, String note, Long approvedById) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        FinancialFile file = getOrThrow(id);
        checkView(file);
        ensureNotClosed(file);
        if (closureType == null) {
            throw new BusinessException("وجهة إنهاء الملف إلزامية");
        }

        // تحديث المحصّل قبل أي فحص للمتبقي
        recomputeStatus(file);
        BigDecimal remaining = file.getRemainingAmount();
        String cleanNote = trimToNull(note);
        Long caseId = null;
        String caseNumber = null;
        Enums.FileStatus finalStatus;

        switch (closureType) {
            case FULL_PAYMENT -> {
                if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException("لا يمكن الإغلاق بالسداد الكامل والمتبقي "
                            + money(remaining) + " درهم");
                }
                finalStatus = Enums.FileStatus.PAID;
            }
            case INSTALLMENT -> {
                if (planRepo.findFirstByFinancialFileIdAndActiveTrue(file.getId()).isEmpty()) {
                    throw new BusinessException("لا يمكن الإنهاء بالتقسيط دون خطة تقسيط نشطة على الملف");
                }
                finalStatus = Enums.FileStatus.INSTALLMENT;
            }
            case LEGAL_OPINION -> {
                if (cleanNote == null) {
                    throw new BusinessException("الإنهاء برأي قانوني يتطلب كتابة الرأي في حقل الملاحظة");
                }
                if (attachmentRepo.findByEntityTypeAndEntityId(ENTITY_TYPE, file.getId()).isEmpty()) {
                    throw new BusinessException("الإنهاء برأي قانوني يتطلب إرفاق مستند واحد على الأقل");
                }
                finalStatus = Enums.FileStatus.CLOSED_OPINION;
            }
            case EXEMPTION -> {
                securityUtils.require(Permission.EXEMPTION_APPROVE);
                if (attachmentRepo.findByEntityTypeAndEntityId(ENTITY_TYPE, file.getId()).isEmpty()) {
                    throw new BusinessException("لا يمكن الإغلاق بالإعفاء دون إرفاق مستند الإعفاء المعتمد");
                }
                User approver = resolveApprover(approvedById);
                file.setApprovedBy(approver);
                file.setExemptedAmount(remaining);
                finalStatus = Enums.FileStatus.EXEMPTED;
            }
            case ESCALATION -> {
                LegalCase created = escalate(file, remaining, cleanNote);
                caseId = created.getId();
                caseNumber = created.getCaseNumber();
                finalStatus = Enums.FileStatus.ESCALATED;
            }
            default -> throw new BusinessException("وجهة إنهاء غير معروفة");
        }

        file.setClosureType(closureType);
        file.setClosureNote(cleanNote);
        file.setClosedAt(LocalDateTime.now());
        file.setClosedBy(securityUtils.currentUser());
        file.setStatus(finalStatus);
        fileRepo.save(file);

        String message;
        if (closureType == Enums.ClosureType.ESCALATION) {
            message = "تم تصعيد الملف المالي إلى القضية رقم " + caseNumber
                    + " مع نقل كل المرفقات وسجلات التواصل";
        } else {
            archiveFile(file, remaining);
            message = "تم إنهاء الملف المالي بـ«" + closureType.label() + "» وأرشفته";
        }

        auditService.log("FINANCIAL_CLOSE", ENTITY_TYPE, file.getId(), file.getFileNumber(),
                "إنهاء الملف المالي بوجهة «" + closureType.label() + "» — المتبقي عند الإنهاء "
                        + money(remaining) + " درهم"
                        + (cleanNote == null ? "" : " — الملاحظة: " + cleanNote));

        return new FinancialDtos.CloseResult(message, closureType.name(), closureType.label(), caseId, caseNumber);
    }

    /** التصعيد: قضية جديدة مع نسخ كل المرفقات وكل سجلات التواصل. */
    private LegalCase escalate(FinancialFile file, BigDecimal remaining, String note) {
        LegalCase legalCase = new LegalCase();
        legalCase.setCaseNumber(numberService.next("CASE", "CASE"));
        legalCase.setCaseType(Enums.CaseType.CIVIL);
        legalCase.setClient(file.getClient());
        legalCase.setOpponent(file.getDebtor());
        legalCase.setAssignedLawyer(file.getAssignedLawyer());
        if (file.getAssignedLawyer() != null) {
            legalCase.setAssignmentReason("منقول مع الملف المالي رقم " + file.getFileNumber());
        }
        legalCase.setSourceFinancialFileId(file.getId());
        legalCase.setSubject(file.getSubject() == null || file.getSubject().isBlank()
                ? "مطالبة مالية — الملف " + file.getFileNumber()
                : file.getSubject());
        legalCase.setDescription(buildEscalationDescription(file, remaining, note));
        legalCase.setClaimAmount(remaining);
        legalCase.setStatus(Enums.CaseStatus.OPEN);
        legalCase.setOpenedAt(LocalDate.now());
        caseRepo.save(legalCase);

        storageService.copyAll(ENTITY_TYPE, file.getId(), CASE_ENTITY_TYPE, legalCase.getId());

        int copied = 0;
        for (Communication source : commRepo.findByFinancialFileIdOrderByCommDateDesc(file.getId())) {
            Communication copy = new Communication();
            copy.setCaseId(legalCase.getId());
            copy.setType(source.getType());
            copy.setCommDate(source.getCommDate());
            copy.setSummary(source.getSummary());
            copy.setOutcome(source.getOutcome());
            copy.setContactPerson(source.getContactPerson());
            copy.setReferenceNo(source.getReferenceNo());
            copy.setCopiedFromId(source.getId());
            commRepo.save(copy);
            copied++;
        }

        file.setEscalatedCaseId(legalCase.getId());

        auditService.log("FINANCIAL_ESCALATE", CASE_ENTITY_TYPE, legalCase.getId(), legalCase.getCaseNumber(),
                "استحداث القضية " + legalCase.getCaseNumber() + " من الملف المالي " + file.getFileNumber()
                        + " بمبلغ مطالبة " + money(remaining) + " درهم، ونقل " + copied + " سجل تواصل وكل المرفقات");
        return legalCase;
    }

    private String buildEscalationDescription(FinancialFile file, BigDecimal remaining, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("قضية مستحدثة بالتصعيد من الملف المالي ").append(file.getFileNumber()).append(".\n");
        sb.append("مبلغ المطالبة الأصلي: ").append(money(file.getClaimAmount())).append(" درهم.\n");
        sb.append("المحصّل ودياً: ").append(money(file.getPaidAmount())).append(" درهم.\n");
        sb.append("المتبقي المطالب به قضائياً: ").append(money(remaining)).append(" درهم.\n");
        if (file.getDescription() != null && !file.getDescription().isBlank()) {
            sb.append("تفاصيل المطالبة: ").append(file.getDescription()).append('\n');
        }
        if (note != null) {
            sb.append("مبرر التصعيد: ").append(note);
        }
        String text = sb.toString();
        return text.length() > 3000 ? text.substring(0, 3000) : text;
    }

    /** أرشفة الملف المنتهي (عدا التصعيد) عبر خدمة الأرشيف. */
    private void archiveFile(FinancialFile file, BigDecimal remaining) {
        String title = "ملف مالي " + file.getFileNumber() + " — " + safeName(file.getClient());
        String summary = "إنهاء بـ«" + (file.getClosureType() == null ? "" : file.getClosureType().label())
                + "» — مطالبة " + money(file.getClaimAmount())
                + " درهم، محصّل " + money(file.getPaidAmount())
                + " درهم، متبقٍ " + money(remaining) + " درهم";

        StringBuilder content = new StringBuilder();
        content.append("رقم الملف: ").append(file.getFileNumber()).append('\n');
        content.append("الموكل: ").append(safeName(file.getClient())).append('\n');
        content.append("المدين: ").append(safeName(file.getDebtor())).append('\n');
        content.append("المحامي المسؤول: ")
                .append(file.getAssignedLawyer() == null ? "غير مسند" : file.getAssignedLawyer().getFullName())
                .append('\n');
        content.append("الموضوع: ").append(file.getSubject() == null ? "-" : file.getSubject()).append('\n');
        content.append("مبلغ المطالبة: ").append(money(file.getClaimAmount())).append(" درهم\n");
        content.append("المحصّل: ").append(money(file.getPaidAmount())).append(" درهم\n");
        content.append("المعفى: ").append(money(file.getExemptedAmount())).append(" درهم\n");
        content.append("المتبقي: ").append(money(remaining)).append(" درهم\n");
        content.append("تاريخ الفتح: ").append(file.getOpenedAt()).append('\n');
        content.append("وجهة الإنهاء: ")
                .append(file.getClosureType() == null ? "-" : file.getClosureType().label()).append('\n');
        if (file.getClosureNote() != null) {
            content.append("ملاحظة الإنهاء: ").append(file.getClosureNote()).append('\n');
        }
        String body = content.toString();
        if (body.length() > 4000) {
            body = body.substring(0, 4000);
        }

        archiveService.archiveSource(ENTITY_TYPE, file.getId(), title, summary, body,
                LocalDate.now(), safeName(file.getClient()));
        file.setArchived(true);
        fileRepo.save(file);
    }

    // ==================================================================
    // الصلاحيات والحماية
    // ==================================================================

    /** المحامي يرى ملفاته فقط ما لم يملك FINANCIAL_VIEW_ALL. */
    public void checkView(FinancialFile file) {
        requireViewPermission();
        if (securityUtils.has(Permission.FINANCIAL_VIEW_ALL)) {
            return;
        }
        Long me = securityUtils.currentUserId();
        User lawyer = file.getAssignedLawyer();
        if (lawyer == null || lawyer.getId() == null || !lawyer.getId().equals(me)) {
            throw new ForbiddenException("لا تملك صلاحية الاطلاع على هذا الملف المالي — تظهر لك الملفات المسندة إليك فقط");
        }
    }

    private void requireViewPermission() {
        if (!securityUtils.has(Permission.FINANCIAL_VIEW) && !securityUtils.has(Permission.FINANCIAL_VIEW_ALL)) {
            throw new ForbiddenException("لا تملك صلاحية عرض الملفات المالية");
        }
    }

    /** ما وُقِّع لا يُكتب فوقه: أي تعديل على ملف مغلق مرفوض. */
    public void ensureNotClosed(FinancialFile file) {
        if (file.getClosedAt() != null) {
            String how = file.getClosureType() == null ? "" : " بـ«" + file.getClosureType().label() + "»";
            throw new BusinessException("الملف المالي " + file.getFileNumber() + " منتهٍ" + how
                    + " ولا يقبل التعديل — ما وُقِّع لا يُكتب فوقه");
        }
    }

    /** الدفعات مسموحة على الملفات المفتوحة، وعلى الملف المنتهي بالتقسيط لتحصيل أقساطه. */
    public boolean paymentsAllowed(FinancialFile file) {
        return file.getClosedAt() == null || file.getClosureType() == Enums.ClosureType.INSTALLMENT;
    }

    /** يرمي رسالة عربية إذا كانت حركة الدفعات ممنوعة على هذا الملف. */
    public void ensurePaymentsAllowed(FinancialFile file) {
        if (!paymentsAllowed(file)) {
            String how = file.getClosureType() == null ? "" : "«" + file.getClosureType().label() + "»";
            throw new BusinessException("لا يمكن إجراء حركة مالية على الملف " + file.getFileNumber()
                    + " لأنه منتهٍ بـ" + how + " — ما وُقِّع لا يُكتب فوقه");
        }
    }

    // ==================================================================
    // أدوات داخلية ومشتركة داخل الحزمة
    // ==================================================================

    private void applyRequest(FinancialFile file, FinancialDtos.FinancialFileRequest req) {
        if (req.claimAmount() == null || req.claimAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("مبلغ المطالبة يجب أن يكون أكبر من صفر");
        }
        if (req.subject() == null || req.subject().isBlank()) {
            throw new BusinessException("موضوع المطالبة إلزامي");
        }
        Party client = partyRepo.findById(requireId(req.clientId(), "الموكل"))
                .orElseThrow(() -> new NotFoundException("الموكل المحدد غير موجود"));
        if (client.getKind() != Party.Kind.CLIENT) {
            throw new BusinessException("الطرف المحدد كموكل ليس مسجلاً ضمن الموكلين");
        }
        Party debtor = partyRepo.findById(requireId(req.debtorId(), "المدين"))
                .orElseThrow(() -> new NotFoundException("المدين المحدد غير موجود"));
        if (debtor.getKind() != Party.Kind.DEBTOR) {
            throw new BusinessException("الطرف المحدد كمدين ليس مسجلاً ضمن المدينين");
        }

        file.setClient(client);
        file.setDebtor(debtor);
        file.setClaimAmount(req.claimAmount().setScale(2, RoundingMode.HALF_UP));
        file.setSubject(req.subject().trim());
        file.setDescription(trimToNull(req.description()));

        if (req.assignedLawyerId() == null) {
            file.setAssignedLawyer(null);
        } else {
            file.setAssignedLawyer(userRepo.findById(req.assignedLawyerId())
                    .orElseThrow(() -> new NotFoundException("المحامي المحدد غير موجود")));
        }
    }

    private Long requireId(Long value, String labelAr) {
        if (value == null) {
            throw new BusinessException("تحديد " + labelAr + " إلزامي");
        }
        return value;
    }

    private User resolveApprover(Long approvedById) {
        if (approvedById == null) {
            return securityUtils.currentUser();
        }
        User approver = userRepo.findById(approvedById)
                .orElseThrow(() -> new NotFoundException("المستخدم المعتمد للإعفاء غير موجود"));
        if (!approver.has(Permission.EXEMPTION_APPROVE)) {
            throw new BusinessException("المستخدم " + approver.getFullName()
                    + " لا يملك صلاحية الموافقة على الإعفاء");
        }
        return approver;
    }

    private List<Attachment> loadAttachments(List<Long> ids) {
        List<Attachment> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (Long attachmentId : ids) {
            if (attachmentId == null) {
                continue;
            }
            result.add(attachmentRepo.findById(attachmentId)
                    .orElseThrow(() -> new NotFoundException("المرفق رقم " + attachmentId + " غير موجود")));
        }
        return result;
    }

    private void linkAttachments(List<Attachment> attachments, Long fileId) {
        for (Attachment a : attachments) {
            Long currentId = a.getEntityId();
            boolean free = currentId == null || currentId == 0L;
            boolean mine = ENTITY_TYPE.equals(a.getEntityType()) && fileId.equals(currentId);
            if (!free && !mine) {
                throw new BusinessException("المرفق «" + a.getFileName()
                        + "» مرتبط بسجل آخر ولا يمكن ربطه بهذا الملف المالي");
            }
            a.setEntityType(ENTITY_TYPE);
            a.setEntityId(fileId);
            attachmentRepo.save(a);
        }
    }

    private boolean matches(FinancialFile f, String needle) {
        return contains(f.getFileNumber(), needle)
                || contains(f.getSubject(), needle)
                || contains(f.getDescription(), needle)
                || (f.getClient() != null && contains(f.getClient().getName(), needle))
                || (f.getDebtor() != null && contains(f.getDebtor().getName(), needle))
                || (f.getDebtor() != null && contains(f.getDebtor().getIdNumber(), needle))
                || (f.getAssignedLawyer() != null && contains(f.getAssignedLawyer().getFullName(), needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean isCategory(String category, String expected) {
        return category != null && category.trim().contains(expected);
    }

    private static String safeName(Party party) {
        return party == null ? "-" : party.getName();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** تنسيق المبالغ في الرسائل العربية: 12,500.00 */
    static String money(BigDecimal value) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(nz(value).setScale(2, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------
    // التحويل إلى DTO
    // ------------------------------------------------------------------

    static FinancialDtos.FinancialFileRow toRow(FinancialFile f) {
        return new FinancialDtos.FinancialFileRow(
                f.getId(),
                f.getFileNumber(),
                f.getClient() == null ? null : f.getClient().getId(),
                safeName(f.getClient()),
                f.getDebtor() == null ? null : f.getDebtor().getId(),
                safeName(f.getDebtor()),
                f.getAssignedLawyer() == null ? null : f.getAssignedLawyer().getId(),
                f.getAssignedLawyer() == null ? null : f.getAssignedLawyer().getFullName(),
                nz(f.getClaimAmount()),
                nz(f.getPaidAmount()),
                nz(f.getExemptedAmount()),
                f.getRemainingAmount(),
                f.getSubject(),
                f.getDescription(),
                f.getStatus() == null ? null : f.getStatus().name(),
                f.getStatus() == null ? null : f.getStatus().label(),
                f.getOpenedAt(),
                f.getClosedAt(),
                f.getClosureType() == null ? null : f.getClosureType().name(),
                f.getClosureType() == null ? null : f.getClosureType().label(),
                f.getClosureNote(),
                f.getClosedBy() == null ? null : f.getClosedBy().getFullName(),
                f.getApprovedBy() == null ? null : f.getApprovedBy().getFullName(),
                f.getEscalatedCaseId(),
                f.isArchived());
    }

    static FinancialDtos.PaymentRow toRow(Payment p, Integer installmentSeq) {
        return new FinancialDtos.PaymentRow(
                p.getId(),
                p.getFinancialFileId(),
                p.getExecutionFileId(),
                p.getReceiptNumber(),
                nz(p.getAmount()),
                p.getPaymentDate(),
                p.getMethod() == null ? null : p.getMethod().name(),
                p.getMethod() == null ? null : p.getMethod().label(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getStatus() == null ? null : p.getStatus().label(),
                p.getReferenceNo(),
                p.getBankName(),
                p.getPayerName(),
                p.getNotes(),
                p.getInstallmentId(),
                installmentSeq,
                p.getReceivedBy() == null ? null : p.getReceivedBy().getFullName(),
                p.getConfirmedBy() == null ? null : p.getConfirmedBy().getFullName(),
                p.getConfirmedAt(),
                p.getBouncedAt(),
                p.getBounceReason());
    }

    static FinancialDtos.InstallmentRow toRow(Installment i) {
        BigDecimal remaining = nz(i.getAmount()).subtract(nz(i.getPaidAmount())).max(BigDecimal.ZERO);
        boolean overdue = i.getStatus() != Enums.InstallmentStatus.PAID
                && i.getStatus() != Enums.InstallmentStatus.CANCELLED
                && i.getDueDate() != null
                && i.getDueDate().isBefore(LocalDate.now());
        return new FinancialDtos.InstallmentRow(
                i.getId(),
                i.getPlan() == null ? null : i.getPlan().getId(),
                i.getSeq(),
                i.getDueDate(),
                nz(i.getAmount()),
                nz(i.getPaidAmount()),
                remaining,
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getStatus() == null ? null : i.getStatus().label(),
                i.getCancelReason(),
                overdue);
    }

    static FinancialDtos.PaymentPlanRow toRow(PaymentPlan plan, List<Installment> installments) {
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (Installment i : installments) {
            if (i.getStatus() != Enums.InstallmentStatus.CANCELLED) {
                paidTotal = paidTotal.add(nz(i.getPaidAmount()));
            }
        }
        paidTotal = paidTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingTotal = nz(plan.getTotalAmount()).subtract(paidTotal).max(BigDecimal.ZERO);
        return new FinancialDtos.PaymentPlanRow(
                plan.getId(),
                nz(plan.getTotalAmount()),
                plan.getInstallmentsCount(),
                plan.getStartDate(),
                plan.getIntervalMonths(),
                plan.getPaymentMethod() == null ? null : plan.getPaymentMethod().name(),
                plan.getPaymentMethod() == null ? null : plan.getPaymentMethod().label(),
                plan.getNotes(),
                plan.isActive(),
                paidTotal,
                remainingTotal);
    }

    static FinancialDtos.CommunicationRow toRow(Communication c) {
        return new FinancialDtos.CommunicationRow(
                c.getId(),
                c.getType() == null ? null : c.getType().name(),
                c.getType() == null ? null : c.getType().label(),
                c.getCommDate(),
                c.getSummary(),
                c.getOutcome(),
                c.getContactPerson(),
                c.getReferenceNo(),
                c.getCreatedBy());
    }

    static FinancialDtos.AttachmentRow toRow(Attachment a) {
        return new FinancialDtos.AttachmentRow(
                a.getId(),
                a.getFileName(),
                a.getContentType(),
                a.getFileSize(),
                a.getCategory(),
                a.getDescription(),
                a.getCreatedAt());
    }
}
