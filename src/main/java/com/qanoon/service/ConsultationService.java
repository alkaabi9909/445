package com.qanoon.service;

import com.qanoon.common.BusinessException;
import com.qanoon.common.EnumParser;
import com.qanoon.common.ForbiddenException;
import com.qanoon.common.NotFoundException;
import com.qanoon.common.NotificationService;
import com.qanoon.common.NumberService;
import com.qanoon.common.PageResult;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.StorageService;
import com.qanoon.common.AuditService;
import com.qanoon.domain.Attachment;
import com.qanoon.domain.Consultation;
import com.qanoon.domain.ConsultationAction;
import com.qanoon.domain.Enums;
import com.qanoon.domain.Party;
import com.qanoon.domain.Permission;
import com.qanoon.domain.User;
import com.qanoon.dto.ConsultationDtos;
import com.qanoon.repo.ConsultationActionRepository;
import com.qanoon.repo.ConsultationRepository;
import com.qanoon.repo.PartyRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * المسار الثالث: الاستشارات القانونية.
 *
 * <p>دورة الحياة السبع:
 * RECEIVED ← STUDYING ← UNDER_REVIEW ← (RETURNED ← STUDYING)* ← APPROVED ← SIGNED ← SENT ← ARCHIVED،
 * وكل انتقال يكتب {@link ConsultationAction} ويُسجَّل في سجل النشاطات.
 *
 * <p>مبدآن ملزمان:
 * <ul>
 *   <li><b>المراجعة المزدوجة</b>: لا يجوز أن يراجع كاتب الرأي رأيه.</li>
 *   <li><b>القفل بعد التوقيع</b>: ما وُقِّع لا يُكتب فوقه ولو من المدير — التصحيح برأي جديد.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ConsultationService {

    /** نوع الكيان المستخدم في المرفقات والأرشيف وسجل النشاطات */
    public static final String ENTITY_TYPE = "CONSULTATION";

    private static final String SEQ_KEY = "CON";
    private static final String SEQ_PREFIX = "CON";
    private static final String LOCKED_MESSAGE =
            "الرأي موقّع ومقفل — لا يُعدَّل ولو من المدير، وأي تصحيح يكون برأي جديد";

    private final ConsultationRepository consultationRepository;
    private final ConsultationActionRepository consultationActionRepository;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final AssignmentEngine assignmentEngine;
    private final ArchiveService archiveService;
    private final NumberService numberService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final SecurityUtils securityUtils;

    /* ============================ القراءة ============================ */

    /**
     * قائمة الاستشارات مع الترشيح بالحالة والبحث النصي.
     * من لا يملك {@code CONSULT_VIEW_ALL} يرى استشاراته فقط، سواء كمستشار أو كمراجع.
     */
    @Transactional(readOnly = true)
    public PageResult list(String status, String q, int page, int size) {
        securityUtils.require(Permission.CONSULT_VIEW);
        User me = securityUtils.currentUser();
        boolean viewAll = securityUtils.has(Permission.CONSULT_VIEW_ALL);
        Enums.ConsultStatus wanted = parseStatus(status);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<Consultation> filtered = new ArrayList<>();
        for (Consultation c : consultationRepository.findAll()) {
            if (!viewAll && !isMine(c, me)) {
                continue;
            }
            if (wanted != null && c.getStatus() != wanted) {
                continue;
            }
            if (!needle.isEmpty() && !matches(c, needle)) {
                continue;
            }
            filtered.add(c);
        }

        Comparator<Consultation> byDate = Comparator.<Consultation, LocalDate>comparing(
                Consultation::getReceivedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Consultation> byId = Comparator.<Consultation, Long>comparing(
                Consultation::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        filtered.sort(byDate.reversed().thenComparing(byId.reversed()));

        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        int safePage = Math.max(page, 0);
        long total = filtered.size();
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<Consultation> pageItems = filtered.subList(from, to);
        return new PageResult(ConsultationDtos.rows(pageItems), total, safePage, safeSize);
    }

    /** الاستشارة كاملة مع سجل مراحلها ومرفقاتها. */
    @Transactional(readOnly = true)
    public ConsultationDtos.ConsultationDetail detail(Long id) {
        securityUtils.require(Permission.CONSULT_VIEW);
        Consultation c = require(id);
        requireCanView(c);
        List<ConsultationAction> actions = consultationActionRepository.findByConsultationIdOrderByActedAtAsc(id);
        List<Attachment> attachments = storageService.list(ENTITY_TYPE, id);
        return ConsultationDtos.detail(c, supersedesNumber(c), allowedActions(c), actions, attachments);
    }

    /** ترتيب المرشحين بالدرجات لتعرضه الواجهة قبل الإسناد. */
    @Transactional(readOnly = true)
    public List<ConsultationDtos.CandidateView> suggest(String specialization) {
        securityUtils.require(Permission.CONSULT_ASSIGN);
        List<ConsultationDtos.CandidateView> out = new ArrayList<>();
        for (AssignmentEngine.Candidate c : assignmentEngine.rank(specialization)) {
            out.add(new ConsultationDtos.CandidateView(c.userId(), c.fullName(), c.score(),
                    c.specializationScore(), c.loadScore(), c.seniorityScore(), c.activeCount(),
                    c.years(), c.reason()));
        }
        return out;
    }

    /* ============================ التسجيل ============================ */

    /** تسجيل استشارة جديدة برقم متسلسل، مع إسناد اختياري فوري. */
    @Transactional
    public Consultation create(ConsultationDtos.CreateRequest req) {
        securityUtils.require(Permission.CONSULT_MANAGE);
        if (req == null) {
            throw new BusinessException("بيانات الاستشارة مطلوبة");
        }
        if (req.clientId() == null) {
            throw new BusinessException("الموكل مطلوب لتسجيل الاستشارة");
        }
        if (isBlank(req.subject())) {
            throw new BusinessException("موضوع الاستشارة مطلوب");
        }
        if (isBlank(req.requestText())) {
            throw new BusinessException("نص الطلب مطلوب");
        }
        Party client = partyRepository.findById(req.clientId())
                .orElseThrow(() -> new NotFoundException("الموكل غير موجود"));
        if (client.getKind() != Party.Kind.CLIENT) {
            throw new BusinessException("الطرف المحدد ليس موكلاً — اختر موكلاً لتسجيل الاستشارة");
        }

        User actor = securityUtils.currentUser();
        Consultation c = new Consultation();
        c.setConsultationNumber(numberService.next(SEQ_KEY, SEQ_PREFIX));
        c.setClient(client);
        c.setSubject(cut(req.subject().trim(), 300));
        c.setSpecialization(isBlank(req.specialization()) ? null : cut(req.specialization().trim(), 100));
        c.setRequestText(cut(req.requestText().trim(), 4000));
        c.setPriority(parsePriority(req.priority()));
        c.setStatus(Enums.ConsultStatus.RECEIVED);
        c.setReceivedAt(LocalDate.now());
        c.setDueDate(req.dueDate());
        consultationRepository.save(c);

        writeAction(c, "RECEIVE", null, Enums.ConsultStatus.RECEIVED, actor,
                "استلام طلب استشارة من الموكل " + client.getName());
        auditService.log("CONSULT_CREATE", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "تسجيل استشارة جديدة: " + c.getSubject());

        if (req.consultantId() != null) {
            return assign(c.getId(), req.consultantId());
        }
        return c;
    }

    /* ============================ الإسناد ============================ */

    /**
     * إسناد الاستشارة إلى مستشار.
     * إن كان {@code consultantId} فارغاً يُستخدم التوزيع الذكي وتُسجَّل الدرجة،
     * وإن كان محدداً فهو إسناد يدوي يتجاوز الترتيب كلياً.
     */
    @Transactional
    public Consultation assign(Long id, Long consultantId) {
        securityUtils.require(Permission.CONSULT_ASSIGN);
        Consultation c = require(id);
        assertNotLocked(c);

        User actor = securityUtils.currentUser();
        User target;
        Double score;
        String reason;
        boolean manual;

        if (consultantId == null) {
            AssignmentEngine.Candidate best = assignmentEngine.best(c.getSpecialization());
            if (best == null) {
                throw new BusinessException(
                        "لا يوجد مستشار متاح للإسناد التلقائي — تأكد من وجود مستخدمين نشطين يملكون صلاحية إدارة الاستشارات");
            }
            target = userRepository.findById(best.userId())
                    .orElseThrow(() -> new NotFoundException("المستشار المرشّح غير موجود"));
            score = best.score();
            reason = best.reason();
            manual = false;
        } else {
            target = userRepository.findById(consultantId)
                    .orElseThrow(() -> new NotFoundException("المستشار غير موجود"));
            if (!target.isActive()) {
                throw new BusinessException("لا يمكن الإسناد إلى مستخدم غير نشط");
            }
            if (!target.has(Permission.CONSULT_MANAGE)) {
                throw new BusinessException("المستخدم المحدد لا يملك صلاحية إدارة الاستشارات");
            }
            score = null;
            reason = "إسناد يدوي بواسطة " + actor.getFullName();
            manual = true;
        }

        Enums.ConsultStatus from = c.getStatus();
        c.setConsultant(target);
        c.setAssignmentReason(cut(reason, 600));
        c.setAssignmentScore(score);
        c.setAssignedManually(manual);
        writeAction(c, "ASSIGN", from, from, actor,
                "إسناد إلى " + target.getFullName() + " — " + reason);

        if (from == Enums.ConsultStatus.RECEIVED) {
            c.setStatus(Enums.ConsultStatus.STUDYING);
            writeAction(c, "START_STUDY", Enums.ConsultStatus.RECEIVED, Enums.ConsultStatus.STUDYING, actor,
                    "بدء دراسة الطلب من المستشار المسند");
        }
        consultationRepository.save(c);

        auditService.log("CONSULT_ASSIGN", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "إسناد الاستشارة إلى " + target.getFullName() + " — " + reason);
        notificationService.push(target.getId(), Enums.NotificationType.TASK,
                "استشارة مسندة إليك",
                "أُسندت إليك الاستشارة " + c.getConsultationNumber() + " بشأن: " + c.getSubject(),
                ENTITY_TYPE, c.getId(), c.getDueDate(),
                "CONSULT_ASSIGN_" + c.getId() + "_" + target.getId());
        return c;
    }

    /* ============================ كتابة الرأي ============================ */

    /** حفظ نص الرأي القانوني — المستشار المسند فقط (أو من يملك صلاحية الإسناد). */
    @Transactional
    public Consultation saveOpinion(Long id, String opinionText) {
        Consultation c = require(id);
        assertNotLocked(c);
        User actor = requireConsultantOrAssigner(c);
        if (c.getStatus() != Enums.ConsultStatus.STUDYING && c.getStatus() != Enums.ConsultStatus.RETURNED) {
            throw new BusinessException("لا يُكتب الرأي إلا في مرحلة الدراسة أو بعد الإعادة بملاحظات");
        }
        if (isBlank(opinionText)) {
            throw new BusinessException("نص الرأي القانوني مطلوب");
        }

        if (c.getStatus() == Enums.ConsultStatus.RETURNED) {
            c.setStatus(Enums.ConsultStatus.STUDYING);
            writeAction(c, "START_STUDY", Enums.ConsultStatus.RETURNED, Enums.ConsultStatus.STUDYING, actor,
                    "معالجة ملاحظات المراجعة وإعادة الدراسة");
        }
        c.setOpinionText(cut(opinionText.trim(), 4000));
        c.setOpinionWrittenAt(LocalDateTime.now());
        consultationRepository.save(c);

        writeAction(c, "WRITE_OPINION", Enums.ConsultStatus.STUDYING, Enums.ConsultStatus.STUDYING, actor,
                "حفظ نص الرأي القانوني");
        auditService.log("CONSULT_OPINION", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "حفظ الرأي القانوني بواسطة " + actor.getFullName());
        return c;
    }

    /** إرسال الرأي للمراجعة — يشترط نص رأي غير فارغ. */
    @Transactional
    public Consultation submitForReview(Long id) {
        Consultation c = require(id);
        assertNotLocked(c);
        User actor = requireConsultantOrAssigner(c);
        if (c.getStatus() != Enums.ConsultStatus.STUDYING && c.getStatus() != Enums.ConsultStatus.RETURNED) {
            throw new BusinessException("لا يُرسل الرأي للمراجعة إلا من مرحلة الدراسة");
        }
        if (isBlank(c.getOpinionText())) {
            throw new BusinessException("لا يمكن إرسال الرأي للمراجعة قبل كتابة نصه");
        }

        Enums.ConsultStatus from = c.getStatus();
        c.setStatus(Enums.ConsultStatus.UNDER_REVIEW);
        consultationRepository.save(c);

        writeAction(c, "SUBMIT_REVIEW", from, Enums.ConsultStatus.UNDER_REVIEW, actor,
                "إرسال الرأي للمراجعة");
        auditService.log("CONSULT_SUBMIT_REVIEW", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "إرسال الرأي للمراجعة بواسطة " + actor.getFullName());
        notifyReviewers(c, actor);
        return c;
    }

    /* ============================ المراجعة المزدوجة ============================ */

    /**
     * مراجعة الرأي: اعتماد أو إعادة بملاحظات.
     * لا يجوز أن يراجع كاتب الرأي رأيه، وتُشترط صلاحية {@code CONSULT_REVIEW}.
     */
    @Transactional
    public Consultation review(Long id, boolean approved, String notes) {
        securityUtils.require(Permission.CONSULT_REVIEW);
        Consultation c = require(id);
        assertNotLocked(c);
        User actor = securityUtils.currentUser();

        if (c.getConsultant() != null && Objects.equals(c.getConsultant().getId(), actor.getId())) {
            throw new BusinessException("لا يجوز أن يراجع كاتب الرأي رأيه — المراجعة يجب أن تكون من مستشار آخر");
        }
        if (c.getStatus() != Enums.ConsultStatus.UNDER_REVIEW) {
            throw new BusinessException("لا تُراجَع إلا الآراء المرسلة للمراجعة");
        }
        if (!approved && isBlank(notes)) {
            throw new BusinessException("ملاحظات الإعادة مطلوبة عند إرجاع الرأي للمستشار");
        }

        LocalDateTime now = LocalDateTime.now();
        Enums.ConsultStatus from = c.getStatus();
        c.setReviewer(actor);
        c.setReviewedAt(now);
        c.setReviewNotes(isBlank(notes) ? null : cut(notes.trim(), 2000));

        if (approved) {
            c.setStatus(Enums.ConsultStatus.APPROVED);
            c.setApprovedBy(actor);
            c.setApprovedAt(now);
            consultationRepository.save(c);
            writeAction(c, "APPROVE", from, Enums.ConsultStatus.APPROVED, actor,
                    isBlank(notes) ? "اعتماد الرأي القانوني" : "اعتماد الرأي القانوني — " + notes.trim());
            auditService.log("CONSULT_APPROVE", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                    "اعتماد الرأي بواسطة " + actor.getFullName());
            if (c.getConsultant() != null) {
                notificationService.push(c.getConsultant().getId(), Enums.NotificationType.INFO,
                        "اعتماد رأيك القانوني",
                        "اعتُمد رأيك في الاستشارة " + c.getConsultationNumber() + " وهو بانتظار التوقيع",
                        ENTITY_TYPE, c.getId(), c.getDueDate(),
                        "CONSULT_APPROVED_" + c.getId());
            }
        } else {
            c.setStatus(Enums.ConsultStatus.RETURNED);
            c.setReturnedCount(c.getReturnedCount() + 1);
            consultationRepository.save(c);
            writeAction(c, "RETURN", from, Enums.ConsultStatus.RETURNED, actor,
                    "إعادة بملاحظات: " + notes.trim());
            auditService.log("CONSULT_RETURN", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                    "إعادة الرأي بملاحظات بواسطة " + actor.getFullName());
            if (c.getConsultant() != null) {
                notificationService.push(c.getConsultant().getId(), Enums.NotificationType.WARNING,
                        "رأيك أُعيد بملاحظات",
                        "أُعيدت الاستشارة " + c.getConsultationNumber() + " بملاحظات المراجعة: " + notes.trim(),
                        ENTITY_TYPE, c.getId(), c.getDueDate(),
                        "CONSULT_RETURNED_" + c.getId() + "_" + c.getReturnedCount());
            }
        }
        return c;
    }

    /* ============================ التوقيع والقفل ============================ */

    /** التوقيع النهائي — يشترط {@code CONSULT_SIGN} والحالة APPROVED، ويقفل الرأي نهائياً. */
    @Transactional
    public Consultation sign(Long id) {
        securityUtils.require(Permission.CONSULT_SIGN);
        Consultation c = require(id);
        assertNotLocked(c);
        if (c.getStatus() != Enums.ConsultStatus.APPROVED) {
            throw new BusinessException("لا يُوقَّع الرأي إلا بعد اعتماده من المراجع");
        }
        if (isBlank(c.getOpinionText())) {
            throw new BusinessException("لا يمكن توقيع رأي بلا نص");
        }

        User actor = securityUtils.currentUser();
        Enums.ConsultStatus from = c.getStatus();
        c.setSignedBy(actor);
        c.setSignedAt(LocalDateTime.now());
        c.setLocked(true);
        c.setStatus(Enums.ConsultStatus.SIGNED);
        consultationRepository.save(c);

        writeAction(c, "SIGN", from, Enums.ConsultStatus.SIGNED, actor,
                "توقيع نهائي بواسطة " + actor.getFullName() + " — الرأي مقفل ولا يُعدَّل");
        auditService.log("CONSULT_SIGN", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "توقيع الرأي وقفله بواسطة " + actor.getFullName());
        if (c.getConsultant() != null && !Objects.equals(c.getConsultant().getId(), actor.getId())) {
            notificationService.push(c.getConsultant().getId(), Enums.NotificationType.INFO,
                    "توقيع الرأي القانوني",
                    "وُقِّع الرأي في الاستشارة " + c.getConsultationNumber() + " وأصبح مقفلاً",
                    ENTITY_TYPE, c.getId(), null,
                    "CONSULT_SIGNED_" + c.getId());
        }
        return c;
    }

    /** إرسال الرأي الموقّع للموكل — أحد الاستثناءين المسموحين بعد التوقيع. */
    @Transactional
    public Consultation send(Long id, String sentTo) {
        securityUtils.require(Permission.CONSULT_MANAGE);
        Consultation c = require(id);
        if (c.getStatus() != Enums.ConsultStatus.SIGNED) {
            throw new BusinessException("لا يُرسل الرأي إلا بعد توقيعه");
        }
        if (isBlank(sentTo)) {
            throw new BusinessException("جهة الإرسال مطلوبة");
        }

        User actor = securityUtils.currentUser();
        Enums.ConsultStatus from = c.getStatus();
        c.setSentTo(cut(sentTo.trim(), 200));
        c.setSentAt(LocalDateTime.now());
        c.setStatus(Enums.ConsultStatus.SENT);
        consultationRepository.save(c);

        writeAction(c, "SEND", from, Enums.ConsultStatus.SENT, actor, "إرسال الرأي إلى " + c.getSentTo());
        auditService.log("CONSULT_SEND", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "إرسال الرأي إلى " + c.getSentTo());
        return c;
    }

    /** أرشفة الاستشارة في الأرشيف الشامل — الاستثناء الثاني المسموح بعد التوقيع. */
    @Transactional
    public Consultation archive(Long id) {
        securityUtils.require(Permission.CONSULT_MANAGE);
        Consultation c = require(id);
        if (c.isArchived() || c.getStatus() == Enums.ConsultStatus.ARCHIVED) {
            throw new BusinessException("الاستشارة مؤرشفة مسبقاً");
        }
        if (c.getStatus() != Enums.ConsultStatus.SENT && c.getStatus() != Enums.ConsultStatus.SIGNED) {
            throw new BusinessException("لا تُؤرشف الاستشارة قبل توقيع الرأي وإرساله");
        }

        User actor = securityUtils.currentUser();
        Enums.ConsultStatus from = c.getStatus();
        archiveService.archiveSource(ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "رأي قانوني: " + c.getSubject(), archiveContent(c),
                c.getClient() != null ? c.getClient().getName() : null);

        c.setArchived(true);
        c.setStatus(Enums.ConsultStatus.ARCHIVED);
        consultationRepository.save(c);

        writeAction(c, "ARCHIVE", from, Enums.ConsultStatus.ARCHIVED, actor, "أرشفة الاستشارة في الأرشيف الشامل");
        auditService.log("CONSULT_ARCHIVE", ENTITY_TYPE, c.getId(), c.getConsultationNumber(),
                "أرشفة الاستشارة " + c.getConsultationNumber());
        return c;
    }

    /**
     * التصحيح: الرأي الموقّع لا يُعدَّل، بل يصدر رأي جديد يشير إلى الأصل عبر {@code supersedesId}.
     */
    @Transactional
    public ConsultationDtos.CorrectionResult createCorrection(Long id) {
        securityUtils.require(Permission.CONSULT_MANAGE);
        Consultation origin = require(id);
        if (!origin.isLocked() && origin.getSignedAt() == null) {
            throw new BusinessException("التصحيح لا يكون إلا لرأي موقّع — الاستشارة غير الموقّعة تُعدَّل مباشرة");
        }

        User actor = securityUtils.currentUser();
        Consultation fresh = new Consultation();
        fresh.setConsultationNumber(numberService.next(SEQ_KEY, SEQ_PREFIX));
        fresh.setClient(origin.getClient());
        fresh.setSubject(origin.getSubject());
        fresh.setSpecialization(origin.getSpecialization());
        fresh.setRequestText(cut("تصحيح للرأي رقم " + origin.getConsultationNumber() + ": "
                + (origin.getRequestText() == null ? "" : origin.getRequestText()), 4000));
        fresh.setPriority(origin.getPriority());
        fresh.setStatus(Enums.ConsultStatus.RECEIVED);
        fresh.setReceivedAt(LocalDate.now());
        fresh.setSupersedesId(origin.getId());
        consultationRepository.save(fresh);

        writeAction(fresh, "RECEIVE", null, Enums.ConsultStatus.RECEIVED, actor,
                "استشارة تصحيحية للرأي الموقّع رقم " + origin.getConsultationNumber());
        writeAction(origin, "CORRECTION", origin.getStatus(), origin.getStatus(), actor,
                "صدر رأي تصحيحي برقم " + fresh.getConsultationNumber());
        auditService.log("CONSULT_CORRECTION", ENTITY_TYPE, fresh.getId(), fresh.getConsultationNumber(),
                "إصدار رأي تصحيحي للرأي الموقّع " + origin.getConsultationNumber());
        if (origin.getConsultant() != null) {
            notificationService.push(origin.getConsultant().getId(), Enums.NotificationType.TASK,
                    "رأي تصحيحي جديد",
                    "صدرت استشارة تصحيحية " + fresh.getConsultationNumber()
                            + " للرأي الموقّع " + origin.getConsultationNumber(),
                    ENTITY_TYPE, fresh.getId(), null,
                    "CONSULT_CORRECTION_" + fresh.getId());
        }
        return new ConsultationDtos.CorrectionResult(fresh.getId(), fresh.getConsultationNumber());
    }

    /* ============================ الحماية والمساعدات ============================ */

    /**
     * القفل بعد التوقيع: تُستدعى في بداية كل دالة تُعدّل الاستشارة، بما في ذلك ما يفعله المدير.
     * الاستثناءان الوحيدان المسموحان بعد التوقيع هما الإرسال والأرشفة.
     */
    private void assertNotLocked(Consultation c) {
        if (c.isLocked()
                || c.getStatus() == Enums.ConsultStatus.SIGNED
                || c.getStatus() == Enums.ConsultStatus.SENT
                || c.getStatus() == Enums.ConsultStatus.ARCHIVED) {
            throw new BusinessException(LOCKED_MESSAGE);
        }
    }

    private Consultation require(Long id) {
        if (id == null) {
            throw new NotFoundException("الاستشارة غير موجودة");
        }
        return consultationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("الاستشارة غير موجودة"));
    }

    private boolean isMine(Consultation c, User me) {
        Long myId = me.getId();
        boolean asConsultant = c.getConsultant() != null && Objects.equals(c.getConsultant().getId(), myId);
        boolean asReviewer = c.getReviewer() != null && Objects.equals(c.getReviewer().getId(), myId);
        return asConsultant || asReviewer;
    }

    private void requireCanView(Consultation c) {
        if (securityUtils.has(Permission.CONSULT_VIEW_ALL)) {
            return;
        }
        if (!isMine(c, securityUtils.currentUser())) {
            throw new ForbiddenException("لا تملك صلاحية الاطلاع على هذه الاستشارة");
        }
    }

    /** كاتب الرأي هو المستشار المسند، ويُسمح كذلك لمن يملك صلاحية إسناد الاستشارات. */
    private User requireConsultantOrAssigner(Consultation c) {
        User actor = securityUtils.currentUser();
        if (c.getConsultant() == null) {
            throw new BusinessException("الاستشارة غير مسندة لمستشار بعد");
        }
        boolean isAssigned = Objects.equals(c.getConsultant().getId(), actor.getId());
        if (!isAssigned && !securityUtils.has(Permission.CONSULT_ASSIGN)) {
            throw new ForbiddenException("الرأي يكتبه المستشار المسند فقط");
        }
        return actor;
    }

    private void writeAction(Consultation c, String action, Enums.ConsultStatus from,
                             Enums.ConsultStatus to, User actor, String notes) {
        ConsultationAction a = new ConsultationAction();
        a.setConsultationId(c.getId());
        a.setAction(action);
        a.setFromStatus(from);
        a.setToStatus(to);
        a.setActor(actor);
        a.setActedAt(LocalDateTime.now());
        a.setNotes(cut(notes, 2000));
        consultationActionRepository.save(a);
    }

    private void notifyReviewers(Consultation c, User actor) {
        for (User u : userRepository.findByActiveTrue()) {
            if (!u.has(Permission.CONSULT_REVIEW)) {
                continue;
            }
            if (Objects.equals(u.getId(), actor.getId())) {
                continue;
            }
            if (c.getConsultant() != null && Objects.equals(u.getId(), c.getConsultant().getId())) {
                continue;
            }
            notificationService.push(u.getId(), Enums.NotificationType.TASK,
                    "رأي بانتظار المراجعة",
                    "الاستشارة " + c.getConsultationNumber() + " بشأن: " + c.getSubject() + " بانتظار مراجعتك",
                    ENTITY_TYPE, c.getId(), c.getDueDate(),
                    "CONSULT_REVIEW_" + c.getId() + "_" + u.getId() + "_" + c.getReturnedCount());
        }
    }

    /** الخطوات المتاحة على الاستشارة في حالتها الحالية — تعرضها الواجهة كأزرار. */
    private List<String> allowedActions(Consultation c) {
        List<String> out = new ArrayList<>();
        if (c.isLocked()) {
            if (c.getStatus() == Enums.ConsultStatus.SIGNED) {
                out.add("SEND");
            }
            if (c.getStatus() == Enums.ConsultStatus.SIGNED || c.getStatus() == Enums.ConsultStatus.SENT) {
                out.add("ARCHIVE");
            }
            out.add("CORRECTION");
            return out;
        }
        switch (c.getStatus()) {
            case RECEIVED -> out.add("ASSIGN");
            case STUDYING, RETURNED -> {
                out.add("ASSIGN");
                out.add("OPINION");
                if (!isBlank(c.getOpinionText())) {
                    out.add("SUBMIT_REVIEW");
                }
            }
            case UNDER_REVIEW -> out.add("REVIEW");
            case APPROVED -> out.add("SIGN");
            default -> {
            }
        }
        return out;
    }

    private String supersedesNumber(Consultation c) {
        if (c.getSupersedesId() == null) {
            return null;
        }
        return consultationRepository.findById(c.getSupersedesId())
                .map(Consultation::getConsultationNumber)
                .orElse(null);
    }

    private String archiveContent(Consultation c) {
        StringBuilder sb = new StringBuilder();
        sb.append("رقم الاستشارة: ").append(c.getConsultationNumber()).append('\n');
        if (c.getClient() != null) {
            sb.append("الموكل: ").append(c.getClient().getName()).append('\n');
        }
        sb.append("الموضوع: ").append(c.getSubject()).append('\n');
        if (!isBlank(c.getSpecialization())) {
            sb.append("التخصص: ").append(c.getSpecialization()).append('\n');
        }
        sb.append("نص الطلب: ").append(c.getRequestText() == null ? "" : c.getRequestText()).append('\n');
        sb.append("الرأي القانوني: ").append(c.getOpinionText() == null ? "" : c.getOpinionText());
        if (c.getSignedBy() != null) {
            sb.append('\n').append("وقّع الرأي: ").append(c.getSignedBy().getFullName());
        }
        return cut(sb.toString(), 4000);
    }

    private Enums.ConsultStatus parseStatus(String value) {
        return EnumParser.optional(Enums.ConsultStatus.class, value, "حالة الاستشارة");
    }

    /**
     * الأولوية اختيارية وتُعامل «عادية» عند غيابها، لكن القيمة المجهولة تُرفض.
     * مطابقة حرفية لأن هذا مسار كتابة.
     */
    private Enums.Priority parsePriority(String value) {
        Enums.Priority parsed = EnumParser.optionalExact(Enums.Priority.class, value, "أولوية الاستشارة");
        return parsed == null ? Enums.Priority.NORMAL : parsed;
    }

    private boolean matches(Consultation c, String needle) {
        return contains(c.getConsultationNumber(), needle)
                || contains(c.getSubject(), needle)
                || contains(c.getSpecialization(), needle)
                || contains(c.getRequestText(), needle)
                || (c.getClient() != null && contains(c.getClient().getName(), needle))
                || (c.getConsultant() != null && contains(c.getConsultant().getFullName(), needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
