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
import com.qanoon.domain.Appeal;
import com.qanoon.domain.Attachment;
import com.qanoon.domain.Communication;
import com.qanoon.domain.Enums;
import com.qanoon.domain.ExecutionFile;
import com.qanoon.domain.Hearing;
import com.qanoon.domain.LegalCase;
import com.qanoon.domain.Party;
import com.qanoon.domain.Permission;
import com.qanoon.domain.User;
import com.qanoon.dto.CaseDtos;
import com.qanoon.repo.AppealRepository;
import com.qanoon.repo.AttachmentRepository;
import com.qanoon.repo.CommunicationRepository;
import com.qanoon.repo.ExecutionFileRepository;
import com.qanoon.repo.HearingRepository;
import com.qanoon.repo.LegalCaseRepository;
import com.qanoon.repo.PartyRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * المسار الثاني: القضايا والجلسات والأحكام والاستئناف وبوابة التحويل إلى التنفيذ.
 *
 * <p>جوهر هذه الخدمة هو {@link #checkTransfer(Long)} — بوابة الشروط الستة.
 * كل تحويل يُعيد تنفيذ البوابة على الخادم ولا يثق بالواجهة إطلاقاً.</p>
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    /** بند واحد من بنود بوابة التحويل: مفتاحه، تسميته العربية، هل تحقق، وسبب المنع إن لم يتحقق. */
    public record CheckItem(String key, String label, boolean passed, String reason) {
    }

    /** نتيجة بوابة التحويل كاملة — تُعاد للواجهة لتعرض كل الموانع نصاً. */
    public record TransferCheck(boolean allowed, java.util.List<CheckItem> checks) {
    }

    public static final String ENTITY_TYPE = "CASE";

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter AR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String KEY_JUDGMENT_RECORDED = "JUDGMENT_RECORDED";
    private static final String KEY_JUDGMENT_FAVOURABLE = "JUDGMENT_FAVOURABLE";
    private static final String KEY_JUDGMENT_FINAL = "JUDGMENT_FINAL";
    private static final String KEY_NO_ACTIVE_APPEAL = "NO_ACTIVE_APPEAL";
    private static final String KEY_NOT_TRANSFERRED = "NOT_TRANSFERRED";
    private static final String KEY_NOT_CLOSED = "NOT_CLOSED";

    private final LegalCaseRepository legalCaseRepository;
    private final HearingRepository hearingRepository;
    private final AppealRepository appealRepository;
    private final CommunicationRepository communicationRepository;
    private final ExecutionFileRepository executionFileRepository;
    private final AttachmentRepository attachmentRepository;
    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final NumberService numberService;
    private final SettingService settingService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ArchiveService archiveService;
    private final ExecutionService executionService;
    private final SecurityUtils securityUtils;

    // ==================================================================================
    //  الاستعراض والبحث
    // ==================================================================================

    /** قائمة القضايا مع ترشيح بالحالة ونص البحث. المحامي يرى قضاياه فقط ما لم يملك CASE_VIEW_ALL. */
    @Transactional(readOnly = true)
    public PageResult list(String status, String q, int page, int size) {
        securityUtils.require(Permission.CASE_VIEW);
        Enums.CaseStatus parsed = parseCaseStatus(status);
        List<LegalCase> source = parsed == null
                ? legalCaseRepository.findAll()
                : legalCaseRepository.findByStatus(parsed);

        boolean viewAll = securityUtils.has(Permission.CASE_VIEW_ALL);
        Long currentUserId = securityUtils.currentUserId();
        String needle = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        List<LegalCase> visible = new ArrayList<>();
        for (LegalCase c : source) {
            if (!viewAll && !isAssignedTo(c, currentUserId)) {
                continue;
            }
            if (needle != null && !matches(c, needle)) {
                continue;
            }
            visible.add(c);
        }
        visible.sort((a, b) -> {
            int cmp = compareDatesDesc(a.getOpenedAt(), b.getOpenedAt());
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(nullToZero(b.getId()), nullToZero(a.getId()));
        });

        int pageIndex = Math.max(page, 0);
        int pageSize = size <= 0 ? 20 : Math.min(size, 200);
        int total = visible.size();
        int from = Math.min(pageIndex * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<CaseDtos.CaseRow> rows = new ArrayList<>();
        for (LegalCase c : visible.subList(from, to)) {
            rows.add(toRow(c));
        }
        return new PageResult(rows, total, pageIndex, pageSize);
    }

    /** القضية مفردة بعد التحقق من صلاحية الاطلاع. */
    @Transactional(readOnly = true)
    public LegalCase get(Long id) {
        LegalCase c = load(id);
        ensureCanView(c);
        return c;
    }

    /** تفاصيل القضية: القضية + الجلسات + الاستئنافات + سجل التواصل المنقول + المرفقات + نتيجة بوابة التحويل. */
    @Transactional(readOnly = true)
    public CaseDtos.CaseDetail detail(Long id) {
        LegalCase c = load(id);
        ensureCanView(c);
        List<Hearing> hearings = hearingRepository.findByCaseIdOrderByHearingDateDesc(c.getId());
        List<Appeal> appeals = appealRepository.findByCaseId(c.getId());
        List<Communication> communications = communicationRepository.findByCaseIdOrderByCommDateDesc(c.getId());
        List<Attachment> attachments = attachmentRepository.findByEntityTypeAndEntityId(ENTITY_TYPE, c.getId());
        return new CaseDtos.CaseDetail(c, hearings, appeals, communications, attachments, buildTransferCheck(c));
    }

    /** جلسات القضية من الأحدث إلى الأقدم. */
    @Transactional(readOnly = true)
    public List<Hearing> hearings(Long caseId) {
        LegalCase c = load(caseId);
        ensureCanView(c);
        return hearingRepository.findByCaseIdOrderByHearingDateDesc(c.getId());
    }

    /** استئنافات القضية. */
    @Transactional(readOnly = true)
    public List<Appeal> appeals(Long caseId) {
        LegalCase c = load(caseId);
        ensureCanView(c);
        return appealRepository.findByCaseId(c.getId());
    }

    // ==================================================================================
    //  إنشاء وتعديل القضية
    // ==================================================================================

    /** إنشاء قضية جديدة برقم متسلسل تلقائي. */
    @Transactional
    public LegalCase create(CaseDtos.CaseRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        CaseDtos.CaseRequest r = requireBody(request);

        LegalCase c = new LegalCase();
        c.setCaseNumber(numberService.next("CASE", "CASE"));
        c.setCaseType(r.caseType() == null ? Enums.CaseType.CIVIL : r.caseType());
        c.setClient(loadParty(r.clientId(), Party.Kind.CLIENT, "الموكل"));
        c.setOpponent(loadParty(r.opponentId(), Party.Kind.DEBTOR, "الخصم"));
        c.setStatus(Enums.CaseStatus.OPEN);
        c.setOpenedAt(r.openedAt() == null ? LocalDate.now() : r.openedAt());
        c.setSourceFinancialFileId(r.sourceFinancialFileId());
        applyEditableFields(c, r);
        applyAssignmentOnCreate(c, r);
        applyRequestedStatus(c, r.status());

        legalCaseRepository.save(c);
        auditService.log("إنشاء قضية", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "موضوع القضية: " + c.getSubject() + " — الموكل: " + c.getClient().getName()
                        + " — الخصم: " + c.getOpponent().getName());
        if (c.getAssignedLawyer() != null) {
            pushAssignmentNotification(c);
        }
        return c;
    }

    /** تعديل بيانات القضية. لا يشمل بيانات الحكم ولا رقم القضية. */
    @Transactional
    public LegalCase update(Long id, CaseDtos.CaseRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        CaseDtos.CaseRequest r = requireBody(request);
        LegalCase c = load(id);
        ensureCanView(c);
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن تعديلها");
        }

        if (r.clientId() != null) {
            c.setClient(loadParty(r.clientId(), Party.Kind.CLIENT, "الموكل"));
        }
        if (r.opponentId() != null) {
            c.setOpponent(loadParty(r.opponentId(), Party.Kind.DEBTOR, "الخصم"));
        }
        if (r.caseType() != null) {
            c.setCaseType(r.caseType());
        }
        if (r.openedAt() != null) {
            c.setOpenedAt(r.openedAt());
        }
        applyEditableFields(c, r);

        boolean closing = r.status() == Enums.CaseStatus.CLOSED;
        if (!closing) {
            applyRequestedStatus(c, r.status());
        }
        legalCaseRepository.save(c);
        auditService.log("تعديل قضية", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "تم تحديث بيانات القضية: " + c.getSubject());

        if (closing) {
            applyClosure(c, null);
        }
        return c;
    }

    /** إسناد القضية إلى محامٍ — سبب الإسناد إلزامي ويُخزَّن في assignmentReason. */
    @Transactional
    public LegalCase assign(Long id, CaseDtos.AssignRequest request) {
        securityUtils.require(Permission.CASE_ASSIGN);
        if (request == null || request.lawyerId() == null) {
            throw new BusinessException("يجب اختيار المحامي المسند إليه");
        }
        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw new BusinessException("سبب الإسناد إلزامي — اكتب سبب إسناد القضية لهذا المحامي");
        }
        LegalCase c = load(id);
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن إسنادها");
        }
        User lawyer = userRepository.findById(request.lawyerId())
                .orElseThrow(() -> new NotFoundException("المحامي المطلوب غير موجود"));
        if (!lawyer.isActive()) {
            throw new BusinessException("لا يمكن الإسناد إلى مستخدم غير نشط: " + lawyer.getFullName());
        }

        String previous = c.getAssignedLawyer() == null ? "بدون إسناد سابق" : c.getAssignedLawyer().getFullName();
        c.setAssignedLawyer(lawyer);
        c.setAssignmentReason(reason);
        legalCaseRepository.save(c);

        auditService.log("إسناد قضية", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "أُسندت من (" + previous + ") إلى (" + lawyer.getFullName() + ") — السبب: " + reason);
        pushAssignmentNotification(c);
        return c;
    }

    // ==================================================================================
    //  الجلسات
    // ==================================================================================

    /** تسجيل جلسة جديدة على القضية. */
    @Transactional
    public Hearing addHearing(Long caseId, CaseDtos.HearingRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        CaseDtos.HearingRequest r = requireHearingBody(request);
        LegalCase c = load(caseId);
        ensureCanView(c);
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن تسجيل جلسات عليها");
        }

        Hearing h = new Hearing();
        h.setCaseId(c.getId());
        h.setType(Enums.HearingType.OTHER);
        applyHearingFields(h, r, c);
        hearingRepository.save(h);

        if (c.getStatus() == Enums.CaseStatus.OPEN) {
            c.setStatus(Enums.CaseStatus.IN_PROGRESS);
            legalCaseRepository.save(c);
        }
        auditService.log("تسجيل جلسة", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "جلسة " + h.getType().label() + " بتاريخ " + h.getHearingDate().format(AR_DATE)
                        + (h.getNextHearingDate() == null ? ""
                        : " — الجلسة القادمة " + h.getNextHearingDate().format(AR_DATE)));
        pushNextHearingNotification(c, h);
        return h;
    }

    /** تعديل جلسة قائمة (نتيجة الجلسة، الحضور، تاريخ الجلسة القادمة...). */
    @Transactional
    public Hearing updateHearing(Long hearingId, CaseDtos.HearingRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        CaseDtos.HearingRequest r = requireHearingBody(request);
        Hearing h = hearingRepository.findById(hearingId)
                .orElseThrow(() -> new NotFoundException("الجلسة غير موجودة"));
        LegalCase c = load(h.getCaseId());
        ensureCanView(c);
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن تعديل جلساتها");
        }

        LocalDate previousNext = h.getNextHearingDate();
        applyHearingFields(h, r, c);
        hearingRepository.save(h);

        auditService.log("تعديل جلسة", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "تعديل جلسة " + h.getHearingDate().format(AR_DATE)
                        + " — الحضور: " + (h.isAttended() ? "نعم" : "لا")
                        + (h.getResult() == null ? "" : " — النتيجة: " + h.getResult()));

        if (h.getNextHearingDate() != null && !h.getNextHearingDate().equals(previousNext)) {
            pushNextHearingNotification(c, h);
        }
        return h;
    }

    // ==================================================================================
    //  توثيق الحكم
    // ==================================================================================

    /**
     * توثيق الحكم: يضبط بيانات الحكم، يحتسب أجل الطعن = تاريخ الحكم + مدة الطعن،
     * يخزّن مدة الطعن على القضية، يضع الحالة "صدر الحكم"، وينبّه المحامي المسند.
     * لا يجوز تعديل الحكم بعد التحويل للتنفيذ.
     */
    @Transactional
    public LegalCase recordJudgment(Long id, CaseDtos.JudgmentRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        if (request == null) {
            throw new BusinessException("بيانات الحكم مطلوبة");
        }
        LegalCase c = load(id);
        ensureCanView(c);

        if (c.getExecutionFileId() != null || c.getStatus() == Enums.CaseStatus.TRANSFERRED) {
            throw new BusinessException("القضية محوّلة إلى التنفيذ — لا يجوز تعديل الحكم بعد التحويل");
        }
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن توثيق الحكم عليها");
        }
        if (request.judgmentDate() == null) {
            throw new BusinessException("تاريخ الحكم مطلوب");
        }
        String judgmentNumber = trimToNull(request.judgmentNumber());
        if (judgmentNumber == null) {
            throw new BusinessException("رقم الحكم مطلوب");
        }
        if (request.judgmentFor() == null) {
            throw new BusinessException("يجب تحديد لصالح من صدر الحكم: لصالح الموكل، لصالح الخصم، أو جزئي");
        }
        if (request.judgmentDate().isAfter(LocalDate.now())) {
            throw new BusinessException("لا يجوز أن يكون تاريخ الحكم في المستقبل");
        }
        if (request.judgmentDate().isBefore(c.getOpenedAt())) {
            throw new BusinessException("تاريخ الحكم لا يجوز أن يسبق تاريخ فتح القضية ("
                    + c.getOpenedAt().format(AR_DATE) + ")");
        }

        int appealDays = settingService.appealDays();
        if (appealDays < 0) {
            appealDays = 0;
        }
        LocalDate deadline = request.judgmentDate().plusDays(appealDays);

        c.setJudgmentDate(request.judgmentDate());
        c.setJudgmentNumber(judgmentNumber);
        c.setJudgmentFor(request.judgmentFor());
        c.setJudgmentAmount(request.judgmentAmount());
        c.setJudgmentSummary(trimToNull(request.judgmentSummary()));
        c.setJudgmentFinal(Boolean.TRUE.equals(request.judgmentFinal()));
        c.setAppealDays(appealDays);
        c.setAppealDeadline(deadline);
        if (c.getStatus() != Enums.CaseStatus.APPEALED) {
            c.setStatus(Enums.CaseStatus.JUDGED);
        }
        legalCaseRepository.save(c);

        auditService.log("توثيق حكم", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "الحكم رقم " + judgmentNumber + " بتاريخ " + request.judgmentDate().format(AR_DATE)
                        + " — " + request.judgmentFor().label()
                        + " — نهائي: " + (c.isJudgmentFinal() ? "نعم" : "لا")
                        + " — أجل الطعن " + appealDays + " يوماً وينتهي في " + deadline.format(AR_DATE));

        if (c.getAssignedLawyer() != null) {
            notificationService.push(
                    c.getAssignedLawyer().getId(),
                    Enums.NotificationType.DEADLINE,
                    "أجل الطعن — القضية " + c.getCaseNumber(),
                    "صدر الحكم رقم " + judgmentNumber + " بتاريخ " + request.judgmentDate().format(AR_DATE)
                            + " (" + request.judgmentFor().label() + "). ينتهي أجل الطعن في "
                            + deadline.format(AR_DATE) + " — أي بعد " + appealDays + " يوماً من تاريخ الحكم.",
                    ENTITY_TYPE,
                    c.getId(),
                    deadline,
                    "CASE-JUDGMENT-" + c.getId() + "-" + judgmentNumber);
        }
        return c;
    }

    // ==================================================================================
    //  الاستئناف
    // ==================================================================================

    /** تسجيل استئناف جديد بحالة "قيد النظر" وتحويل حالة القضية إلى "مستأنفة". */
    @Transactional
    public Appeal addAppeal(Long caseId, CaseDtos.AppealRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        if (request == null) {
            throw new BusinessException("بيانات الاستئناف مطلوبة");
        }
        LegalCase c = load(caseId);
        ensureCanView(c);

        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة — لا يمكن تسجيل استئناف عليها");
        }
        if (c.getExecutionFileId() != null || c.getStatus() == Enums.CaseStatus.TRANSFERRED) {
            throw new BusinessException("القضية محوّلة إلى التنفيذ — لا يمكن تسجيل استئناف عليها");
        }
        if (c.getJudgmentDate() == null || c.getJudgmentNumber() == null) {
            throw new BusinessException("لا يمكن تسجيل استئناف قبل توثيق الحكم");
        }
        if (request.filedDate() == null) {
            throw new BusinessException("تاريخ رفع الاستئناف مطلوب");
        }
        if (request.filedBy() == null) {
            throw new BusinessException("يجب تحديد رافع الاستئناف: الموكل أو الخصم");
        }
        if (request.filedDate().isBefore(c.getJudgmentDate())) {
            throw new BusinessException("تاريخ رفع الاستئناف لا يجوز أن يسبق تاريخ الحكم ("
                    + c.getJudgmentDate().format(AR_DATE) + ")");
        }

        Appeal a = new Appeal();
        a.setCaseId(c.getId());
        a.setAppealNumber(trimToNull(request.appealNumber()));
        a.setFiledDate(request.filedDate());
        a.setFiledBy(request.filedBy());
        a.setStatus(Enums.AppealStatus.PENDING);
        a.setCourt(trimToNull(request.court()));
        a.setNotes(trimToNull(request.notes()));
        appealRepository.save(a);

        c.setStatus(Enums.CaseStatus.APPEALED);
        legalCaseRepository.save(c);

        auditService.log("تسجيل استئناف", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "استئناف رقم " + appealLabel(a) + " مرفوع من " + request.filedBy().label()
                        + " بتاريخ " + request.filedDate().format(AR_DATE)
                        + " — التحويل إلى التنفيذ ممنوع حتى الفصل فيه");

        if (c.getAssignedLawyer() != null) {
            notificationService.push(
                    c.getAssignedLawyer().getId(),
                    Enums.NotificationType.WARNING,
                    "استئناف على القضية " + c.getCaseNumber(),
                    "سُجّل استئناف رقم " + appealLabel(a) + " مرفوع من " + request.filedBy().label()
                            + " بتاريخ " + request.filedDate().format(AR_DATE)
                            + " — التحويل إلى التنفيذ ممنوع حتى الفصل فيه.",
                    ENTITY_TYPE,
                    c.getId(),
                    null,
                    "CASE-APPEAL-" + a.getId());
        }
        return a;
    }

    /** الفصل في الاستئناف: تم الفصل فيه أو مسحوب. إن لم يبقَ استئناف معلّق تعود القضية إلى "صدر الحكم". */
    @Transactional
    public Appeal decideAppeal(Long appealId, CaseDtos.AppealDecisionRequest request) {
        securityUtils.require(Permission.CASE_MANAGE);
        if (request == null || request.status() == null) {
            throw new BusinessException("يجب تحديد نتيجة الفصل في الاستئناف");
        }
        if (request.status() != Enums.AppealStatus.DECIDED && request.status() != Enums.AppealStatus.WITHDRAWN) {
            throw new BusinessException("نتيجة الاستئناف يجب أن تكون: تم الفصل فيه أو مسحوب");
        }
        Appeal a = appealRepository.findById(appealId)
                .orElseThrow(() -> new NotFoundException("الاستئناف غير موجود"));
        if (a.getStatus() != Enums.AppealStatus.PENDING) {
            throw new BusinessException("سبق الفصل في هذا الاستئناف — حالته الحالية: " + a.getStatus().label());
        }
        LegalCase c = load(a.getCaseId());
        ensureCanView(c);

        LocalDate decisionDate = request.decisionDate() == null ? LocalDate.now() : request.decisionDate();
        if (decisionDate.isBefore(a.getFiledDate())) {
            throw new BusinessException("تاريخ الفصل لا يجوز أن يسبق تاريخ رفع الاستئناف ("
                    + a.getFiledDate().format(AR_DATE) + ")");
        }
        a.setStatus(request.status());
        a.setDecisionDate(decisionDate);
        a.setDecisionSummary(trimToNull(request.decisionSummary()));
        appealRepository.save(a);

        List<Appeal> stillPending = appealRepository.findByCaseIdAndStatus(c.getId(), Enums.AppealStatus.PENDING);
        boolean caseReopened = false;
        if (stillPending.isEmpty()
                && c.getStatus() != Enums.CaseStatus.CLOSED
                && c.getStatus() != Enums.CaseStatus.TRANSFERRED
                && c.getJudgmentDate() != null) {
            c.setStatus(Enums.CaseStatus.JUDGED);
            legalCaseRepository.save(c);
            caseReopened = true;
        }

        auditService.log("الفصل في استئناف", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "الاستئناف رقم " + appealLabel(a) + " أصبح " + request.status().label()
                        + " بتاريخ " + decisionDate.format(AR_DATE)
                        + (caseReopened ? " — لم يبقَ استئناف معلّق فعادت حالة القضية إلى صدر الحكم" : ""));
        return a;
    }

    // ==================================================================================
    //  بوابة التحويل إلى التنفيذ — الشروط الستة
    // ==================================================================================

    /**
     * بوابة الشروط الستة. تُعيد كل الموانع بنصوص عربية جاهزة للعرض،
     * ولا ترمي استثناءً حتى تتمكن الواجهة من عرض الحالة كاملة.
     */
    @Transactional(readOnly = true)
    public TransferCheck checkTransfer(Long caseId) {
        LegalCase c = load(caseId);
        ensureCanView(c);
        return buildTransferCheck(c);
    }

    /**
     * التحويل الفعلي إلى ملف تنفيذ. يُعيد تنفيذ البوابة على الخادم ولا يثق بالواجهة إطلاقاً،
     * ثم ينشئ ملف التنفيذ ويربطه بالقضية ويضع حالتها "محوّلة للتنفيذ".
     */
    @Transactional
    public CaseDtos.TransferResult transfer(Long caseId) {
        securityUtils.require(Permission.CASE_TRANSFER);
        LegalCase c = load(caseId);
        ensureCanView(c);

        TransferCheck check = buildTransferCheck(c);
        if (!check.allowed()) {
            List<String> blockers = new ArrayList<>();
            for (CheckItem item : check.checks()) {
                if (!item.passed()) {
                    blockers.add(item.label() + ": " + item.reason());
                }
            }
            throw new BusinessException("لا يمكن تحويل القضية إلى التنفيذ — شروط التحويل غير مكتملة", blockers);
        }

        ExecutionFile file = executionService.createFromCase(c);
        if (file == null || file.getId() == null) {
            throw new BusinessException("تعذّر إنشاء ملف التنفيذ — أعد المحاولة");
        }
        c.setExecutionFileId(file.getId());
        c.setStatus(Enums.CaseStatus.TRANSFERRED);
        legalCaseRepository.save(c);

        auditService.log("تحويل قضية إلى التنفيذ", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "اجتازت القضية شروط التحويل الستة وحُوّلت إلى ملف التنفيذ رقم " + file.getExecutionNumber()
                        + " — الحكم رقم " + c.getJudgmentNumber());

        if (file.getAssignedLawyer() != null) {
            notificationService.push(
                    file.getAssignedLawyer().getId(),
                    Enums.NotificationType.TASK,
                    "ملف تنفيذ جديد " + file.getExecutionNumber(),
                    "حُوّلت القضية " + c.getCaseNumber() + " إلى ملف التنفيذ رقم " + file.getExecutionNumber()
                            + " — الموكل: " + c.getClient().getName() + ".",
                    "EXECUTION",
                    file.getId(),
                    null,
                    "EXEC-FROM-CASE-" + c.getId());
        }
        return new CaseDtos.TransferResult(file.getId(), file.getExecutionNumber());
    }

    // ==================================================================================
    //  الإغلاق والأرشفة
    // ==================================================================================

    /** إغلاق القضية وأرشفتها في الأرشيف الشامل. */
    @Transactional
    public LegalCase close(Long id, String note) {
        securityUtils.require(Permission.CASE_MANAGE);
        LegalCase c = load(id);
        ensureCanView(c);
        if (c.getStatus() == Enums.CaseStatus.CLOSED) {
            throw new BusinessException("القضية مغلقة مسبقاً");
        }
        applyClosure(c, note);
        return c;
    }

    private void applyClosure(LegalCase c, String note) {
        List<Appeal> pending = appealRepository.findByCaseIdAndStatus(c.getId(), Enums.AppealStatus.PENDING);
        if (!pending.isEmpty()) {
            throw new BusinessException("لا يمكن إغلاق القضية ويوجد استئناف معلّق رقم "
                    + appealLabel(pending.get(0)) + " — يجب الفصل فيه أولاً");
        }
        String closureNote = trimToNull(note);
        c.setStatus(Enums.CaseStatus.CLOSED);
        c.setClosedAt(LocalDateTime.now());
        c.setClosureNote(closureNote);
        c.setArchived(true);
        legalCaseRepository.save(c);

        archiveService.archiveSource(
                Enums.ArchiveType.CASE,
                ENTITY_TYPE,
                c.getId(),
                c.getCaseNumber(),
                "القضية " + c.getCaseNumber() + " — " + c.getSubject(),
                buildArchiveContent(c, closureNote),
                c.getClient() == null ? null : c.getClient().getName(),
                c.getCourt());

        auditService.log("إغلاق قضية", ENTITY_TYPE, c.getId(), c.getCaseNumber(),
                "أُغلقت القضية وأُرشفت" + (closureNote == null ? "" : " — سبب الإغلاق: " + closureNote));
    }

    private String buildArchiveContent(LegalCase c, String closureNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("رقم القضية: ").append(c.getCaseNumber()).append('\n');
        if (c.getCourtCaseNumber() != null) {
            sb.append("رقم القضية لدى المحكمة: ").append(c.getCourtCaseNumber()).append('\n');
        }
        sb.append("نوع القضية: ").append(c.getCaseType() == null ? "غير محدد" : c.getCaseType().label()).append('\n');
        sb.append("المحكمة: ").append(c.getCourt() == null ? "غير محددة" : c.getCourt()).append('\n');
        sb.append("الموكل: ").append(c.getClient() == null ? "غير محدد" : c.getClient().getName()).append('\n');
        sb.append("الخصم: ").append(c.getOpponent() == null ? "غير محدد" : c.getOpponent().getName()).append('\n');
        sb.append("المحامي المسند: ")
                .append(c.getAssignedLawyer() == null ? "غير مسند" : c.getAssignedLawyer().getFullName()).append('\n');
        sb.append("الموضوع: ").append(c.getSubject()).append('\n');
        if (c.getDescription() != null) {
            sb.append("التفاصيل: ").append(c.getDescription()).append('\n');
        }
        sb.append("تاريخ الفتح: ").append(c.getOpenedAt().format(AR_DATE)).append('\n');
        if (c.getJudgmentDate() != null) {
            sb.append("الحكم: رقم ").append(c.getJudgmentNumber())
                    .append(" بتاريخ ").append(c.getJudgmentDate().format(AR_DATE))
                    .append(" — ").append(c.getJudgmentFor() == null ? "غير محدد" : c.getJudgmentFor().label())
                    .append(c.isJudgmentFinal() ? " — نهائي" : "").append('\n');
        }
        if (c.getJudgmentSummary() != null) {
            sb.append("ملخص الحكم: ").append(c.getJudgmentSummary()).append('\n');
        }
        if (c.getExecutionFileId() != null) {
            sb.append("حُوّلت إلى ملف تنفيذ رقم داخلي: ").append(c.getExecutionFileId()).append('\n');
        }
        if (closureNote != null) {
            sb.append("سبب الإغلاق: ").append(closureNote).append('\n');
        }
        return sb.toString();
    }

    // ==================================================================================
    //  بناء بوابة الشروط الستة
    // ==================================================================================

    private TransferCheck buildTransferCheck(LegalCase c) {
        List<CheckItem> checks = new ArrayList<>();

        // 1) حكم موثّق
        boolean judgmentRecorded = c.getJudgmentDate() != null
                && c.getJudgmentNumber() != null
                && !c.getJudgmentNumber().isBlank();
        checks.add(new CheckItem(KEY_JUDGMENT_RECORDED, "حكم موثّق", judgmentRecorded,
                judgmentRecorded ? null : "لا يوجد حكم موثّق — سجّل تاريخ الحكم ورقمه أولاً"));

        // 2) الحكم ليس لصالح الخصم
        boolean favourable = c.getJudgmentFor() == Enums.JudgmentFor.CLIENT
                || c.getJudgmentFor() == Enums.JudgmentFor.PARTIAL;
        checks.add(new CheckItem(KEY_JUDGMENT_FAVOURABLE, "الحكم ليس لصالح الخصم", favourable,
                favourable ? null : "الحكم صدر لصالح الخصم"));

        // 3) نهائية الحكم — اليوم الأخير نفسه ما زال ضمن المهلة، فالتحويل جائز من اليوم التالي فقط
        LocalDate today = LocalDate.now();
        boolean deadlinePassed = c.getAppealDeadline() != null && today.isAfter(c.getAppealDeadline());
        boolean judgmentFinal = c.isJudgmentFinal() || deadlinePassed;
        String finalReason = null;
        if (!judgmentFinal) {
            finalReason = c.getAppealDeadline() != null
                    ? "الحكم غير نهائي ولم ينقضِ أجل الطعن — ينتهي في " + c.getAppealDeadline().format(ISO_DATE)
                    : "الحكم غير نهائي ولم يُحتسب أجل الطعن — وثّق الحكم أولاً";
        }
        checks.add(new CheckItem(KEY_JUDGMENT_FINAL, "نهائية الحكم", judgmentFinal, finalReason));

        // 4) لا يوجد استئناف مُفعّل — يمنع حتى بعد انقضاء الأجل
        List<Appeal> pending = appealRepository.findByCaseIdAndStatus(c.getId(), Enums.AppealStatus.PENDING);
        boolean noActiveAppeal = pending.isEmpty();
        checks.add(new CheckItem(KEY_NO_ACTIVE_APPEAL, "لا يوجد استئناف مُفعّل", noActiveAppeal,
                noActiveAppeal ? null
                        : "يوجد استئناف مُعلّق رقم " + appealLabel(pending.get(0))
                        + " — التحويل ممنوع حتى الفصل فيه"));

        // 5) لم يسبق التحويل
        ExecutionFile linked = executionFileRepository.findByCaseId(c.getId()).orElse(null);
        boolean notTransferred = c.getExecutionFileId() == null && linked == null;
        String transferReason = null;
        if (!notTransferred) {
            transferReason = "سبق تحويل القضية لملف تنفيذ رقم " + executionLabel(c, linked);
        }
        checks.add(new CheckItem(KEY_NOT_TRANSFERRED, "لم يسبق التحويل", notTransferred, transferReason));

        // 6) القضية غير مغلقة
        boolean notClosed = c.getStatus() != Enums.CaseStatus.CLOSED;
        checks.add(new CheckItem(KEY_NOT_CLOSED, "القضية غير مغلقة", notClosed, notClosed ? null : "القضية مغلقة"));

        boolean allowed = true;
        for (CheckItem item : checks) {
            if (!item.passed()) {
                allowed = false;
                break;
            }
        }
        return new TransferCheck(allowed, List.copyOf(checks));
    }

    private String executionLabel(LegalCase c, ExecutionFile linked) {
        if (linked != null && linked.getExecutionNumber() != null) {
            return linked.getExecutionNumber();
        }
        if (c.getExecutionFileId() != null) {
            ExecutionFile byId = executionFileRepository.findById(c.getExecutionFileId()).orElse(null);
            if (byId != null && byId.getExecutionNumber() != null) {
                return byId.getExecutionNumber();
            }
            return String.valueOf(c.getExecutionFileId());
        }
        return linked == null ? "غير معروف" : String.valueOf(linked.getId());
    }

    private String appealLabel(Appeal a) {
        if (a.getAppealNumber() != null && !a.getAppealNumber().isBlank()) {
            return a.getAppealNumber();
        }
        return String.valueOf(a.getId());
    }

    // ==================================================================================
    //  أدوات داخلية
    // ==================================================================================

    private LegalCase load(Long id) {
        if (id == null) {
            throw new NotFoundException("القضية غير موجودة");
        }
        return legalCaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("القضية غير موجودة"));
    }

    private void ensureCanView(LegalCase c) {
        securityUtils.require(Permission.CASE_VIEW);
        if (securityUtils.has(Permission.CASE_VIEW_ALL)) {
            return;
        }
        if (isAssignedTo(c, securityUtils.currentUserId())) {
            return;
        }
        throw new ForbiddenException("لا تملك صلاحية الاطلاع على هذه القضية — تظهر لك القضايا المسندة إليك فقط");
    }

    private boolean isAssignedTo(LegalCase c, Long userId) {
        return c.getAssignedLawyer() != null && Objects.equals(c.getAssignedLawyer().getId(), userId);
    }

    private void applyEditableFields(LegalCase c, CaseDtos.CaseRequest r) {
        String subject = trimToNull(r.subject());
        if (subject == null && c.getSubject() == null) {
            throw new BusinessException("موضوع القضية مطلوب");
        }
        if (subject != null) {
            c.setSubject(subject);
        }
        c.setCourtCaseNumber(trimToNull(r.courtCaseNumber()));
        c.setCourt(trimToNull(r.court()));
        c.setDescription(trimToNull(r.description()));
        c.setClaimAmount(r.claimAmount());
        c.setFiledAt(r.filedAt());
        if (r.filedAt() != null && r.filedAt().isBefore(c.getOpenedAt())) {
            throw new BusinessException("تاريخ رفع الدعوى لا يجوز أن يسبق تاريخ فتح القضية");
        }
    }

    private void applyAssignmentOnCreate(LegalCase c, CaseDtos.CaseRequest r) {
        if (r.assignedLawyerId() == null) {
            return;
        }
        String reason = trimToNull(r.assignmentReason());
        if (reason == null) {
            throw new BusinessException("سبب الإسناد إلزامي عند تحديد المحامي المسند");
        }
        User lawyer = userRepository.findById(r.assignedLawyerId())
                .orElseThrow(() -> new NotFoundException("المحامي المطلوب غير موجود"));
        if (!lawyer.isActive()) {
            throw new BusinessException("لا يمكن الإسناد إلى مستخدم غير نشط: " + lawyer.getFullName());
        }
        c.setAssignedLawyer(lawyer);
        c.setAssignmentReason(reason);
    }

    /** حالات القضية تُشتق من الإجراءات؛ يدوياً يُسمح فقط بـ "مفتوحة" و"قيد النظر". */
    private void applyRequestedStatus(LegalCase c, Enums.CaseStatus requested) {
        if (requested == null || requested == c.getStatus()) {
            return;
        }
        if (requested == Enums.CaseStatus.OPEN || requested == Enums.CaseStatus.IN_PROGRESS) {
            if (c.getStatus() == Enums.CaseStatus.JUDGED
                    || c.getStatus() == Enums.CaseStatus.APPEALED
                    || c.getStatus() == Enums.CaseStatus.TRANSFERRED) {
                throw new BusinessException("لا يمكن إرجاع القضية إلى " + requested.label()
                        + " بعد بلوغها حالة " + c.getStatus().label());
            }
            c.setStatus(requested);
            return;
        }
        throw new BusinessException("حالة القضية (" + requested.label()
                + ") تُشتق من الإجراءات — تُضبط بتوثيق الحكم أو تسجيل الاستئناف أو التحويل للتنفيذ أو الإغلاق");
    }

    private void applyHearingFields(Hearing h, CaseDtos.HearingRequest r, LegalCase c) {
        if (r.hearingDate() != null) {
            h.setHearingDate(r.hearingDate());
        }
        if (h.getHearingDate() == null) {
            throw new BusinessException("تاريخ الجلسة مطلوب");
        }
        h.setHearingTime(r.hearingTime());
        if (r.type() != null) {
            h.setType(r.type());
        }
        h.setCourt(r.court() == null ? c.getCourt() : trimToNull(r.court()));
        h.setRoom(trimToNull(r.room()));
        h.setNotes(trimToNull(r.notes()));
        h.setResult(trimToNull(r.result()));
        if (r.attended() != null) {
            h.setAttended(r.attended());
        }
        if (r.nextHearingDate() != null && !r.nextHearingDate().isAfter(h.getHearingDate())) {
            throw new BusinessException("تاريخ الجلسة القادمة يجب أن يكون بعد تاريخ الجلسة الحالية");
        }
        h.setNextHearingDate(r.nextHearingDate());
    }

    private void pushNextHearingNotification(LegalCase c, Hearing h) {
        if (h.getNextHearingDate() == null || c.getAssignedLawyer() == null) {
            return;
        }
        String court = h.getCourt() != null ? h.getCourt() : (c.getCourt() != null ? c.getCourt() : "المحكمة غير محددة");
        notificationService.push(
                c.getAssignedLawyer().getId(),
                Enums.NotificationType.DEADLINE,
                "جلسة قادمة — القضية " + c.getCaseNumber(),
                "الجلسة القادمة يوم " + h.getNextHearingDate().format(AR_DATE) + " في " + court
                        + " — الموضوع: " + c.getSubject() + ".",
                ENTITY_TYPE,
                c.getId(),
                h.getNextHearingDate(),
                "HEARING-NEXT-" + h.getId() + "-" + h.getNextHearingDate().format(ISO_DATE));
    }

    private void pushAssignmentNotification(LegalCase c) {
        notificationService.push(
                c.getAssignedLawyer().getId(),
                Enums.NotificationType.TASK,
                "إسناد قضية " + c.getCaseNumber(),
                "أُسندت إليك القضية " + c.getCaseNumber() + " — " + c.getSubject()
                        + " (الموكل: " + c.getClient().getName() + "). سبب الإسناد: " + c.getAssignmentReason(),
                ENTITY_TYPE,
                c.getId(),
                null,
                "CASE-ASSIGN-" + c.getId() + "-" + c.getAssignedLawyer().getId());
    }

    private Party loadParty(Long id, Party.Kind kind, String labelAr) {
        if (id == null) {
            throw new BusinessException("يجب اختيار " + labelAr);
        }
        Party p = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(labelAr + " غير موجود"));
        if (p.getKind() != kind) {
            throw new BusinessException("الطرف المختار ليس من نوع " + labelAr);
        }
        return p;
    }

    private CaseDtos.CaseRequest requireBody(CaseDtos.CaseRequest r) {
        if (r == null) {
            throw new BusinessException("بيانات القضية مطلوبة");
        }
        return r;
    }

    private CaseDtos.HearingRequest requireHearingBody(CaseDtos.HearingRequest r) {
        if (r == null) {
            throw new BusinessException("بيانات الجلسة مطلوبة");
        }
        return r;
    }

    private Enums.CaseStatus parseCaseStatus(String s) {
        return EnumParser.optional(Enums.CaseStatus.class, s, "حالة القضية");
    }

    private boolean matches(LegalCase c, String needle) {
        return contains(c.getCaseNumber(), needle)
                || contains(c.getCourtCaseNumber(), needle)
                || contains(c.getSubject(), needle)
                || contains(c.getDescription(), needle)
                || contains(c.getCourt(), needle)
                || contains(c.getJudgmentNumber(), needle)
                || (c.getClient() != null && contains(c.getClient().getName(), needle))
                || (c.getOpponent() != null && contains(c.getOpponent().getName(), needle))
                || (c.getAssignedLawyer() != null && contains(c.getAssignedLawyer().getFullName(), needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private int compareDatesDesc(LocalDate a, LocalDate b) {
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

    private long nullToZero(Long v) {
        return v == null ? 0L : v;
    }

    private CaseDtos.CaseRow toRow(LegalCase c) {
        return new CaseDtos.CaseRow(
                c.getId(),
                c.getCaseNumber(),
                c.getCourtCaseNumber(),
                c.getCourt(),
                c.getCaseType() == null ? null : c.getCaseType().name(),
                c.getCaseType() == null ? null : c.getCaseType().label(),
                c.getClient() == null ? null : c.getClient().getName(),
                c.getOpponent() == null ? null : c.getOpponent().getName(),
                c.getAssignedLawyer() == null ? null : c.getAssignedLawyer().getFullName(),
                c.getSubject(),
                c.getClaimAmount(),
                c.getStatus() == null ? null : c.getStatus().name(),
                c.getStatus() == null ? null : c.getStatus().label(),
                c.getOpenedAt(),
                c.getFiledAt(),
                c.getJudgmentDate(),
                c.getJudgmentNumber(),
                c.getAppealDeadline(),
                c.isJudgmentFinal(),
                c.getExecutionFileId(),
                c.isArchived());
    }

    /** يعيد النص بعد إزالة الفراغات الطرفية، أو null إن كان فارغاً. */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
