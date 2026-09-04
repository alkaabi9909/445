package com.qanoon.service;

import com.qanoon.common.SecurityUtils;
import com.qanoon.common.SettingService;
import com.qanoon.domain.*;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** لوحة المعلومات: صورة فورية عن عمل المستخدم — والمدير يرى المكتب كله. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FinancialFileRepository fileRepo;
    private final LegalCaseRepository caseRepo;
    private final ExecutionFileRepository execRepo;
    private final ConsultationRepository consultRepo;
    private final HearingRepository hearingRepo;
    private final InstallmentRepository installmentRepo;
    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;
    private final SettingService settings;
    private final SecurityUtils securityUtils;

    // ---------- أشكال البيانات ----------

    public record Metric(String key, String label, long value, String hint) {}

    public record ChartPoint(String label, double value, String color) {}

    public record UrgentItem(String kind, String kindLabel, String title, String subtitle,
                             LocalDate date, Integer daysLeft, String tone,
                             String linkType, Long linkId) {}

    public record TeamRow(Long userId, String name, String roleName, long files,
                          Double avgDays, long activeLoad) {}

    public record Financials(BigDecimal claimed, BigDecimal collected, BigDecimal outstanding,
                             double collectionRate, String currency) {}

    public record Dashboard(List<Metric> workload, List<UrgentItem> urgent, List<TeamRow> team,
                            Financials financials, List<ChartPoint> byType, List<ChartPoint> byStatus,
                            boolean officeWide, String officeName) {}

    // ---------- لوحة المعلومات ----------

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        User me = securityUtils.currentUser();
        boolean all = securityUtils.has(Permission.DASHBOARD_ALL);

        List<FinancialFile> files = visibleFiles(me, all);
        List<LegalCase> cases = visibleCases(me, all);
        List<ExecutionFile> execs = visibleExecutions(me, all);
        List<Consultation> consults = visibleConsultations(me, all);

        List<UrgentItem> urgent = urgent();

        long openFiles = files.stream().filter(f -> !f.isArchived()).count();
        long openCases = cases.stream()
                .filter(c -> c.getStatus() != Enums.CaseStatus.CLOSED && !c.isArchived()).count();
        long openExec = execs.stream()
                .filter(e -> e.getStatus() != Enums.ExecStatus.CLOSED && !e.isArchived()).count();
        long openConsults = consults.stream().filter(Consultation::isActiveWorkload).count();

        List<Metric> workload = List.of(
                new Metric("files", "ملفات مالية مفتوحة", openFiles, "قيد التحصيل الودي"),
                new Metric("cases", "قضايا جارية", openCases, "لم يُفصل فيها نهائياً"),
                new Metric("executions", "ملفات تنفيذ مفتوحة", openExec, "قيد الاستيفاء الجبري"),
                new Metric("consultations", "استشارات جارية", openConsults, "لم توقّع بعد"));

        List<ChartPoint> byType = List.of(
                new ChartPoint("مالية", openFiles, "#0f2c4c"),
                new ChartPoint("قضايا", openCases, "#c8a253"),
                new ChartPoint("تنفيذ", openExec, "#1b4370"),
                new ChartPoint("استشارات", openConsults, "#0f7b52"));

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (FinancialFile f : files) {
            if (f.getStatus() != null) {
                statusCounts.merge(f.getStatus().label(), 1L, Long::sum);
            }
        }
        List<ChartPoint> byStatus = new ArrayList<>();
        String[] palette = {"#0f2c4c", "#c8a253", "#1b4370", "#0f7b52", "#b45309", "#b91c1c", "#6b7280", "#7c3aed"};
        int i = 0;
        for (var e : statusCounts.entrySet()) {
            byStatus.add(new ChartPoint(e.getKey(), e.getValue(), palette[i++ % palette.length]));
        }

        return new Dashboard(workload, urgent, all ? team() : List.of(), financials(files),
                byType, byStatus, all, settings.officeName());
    }

    /** الملفات العاجلة: جلسات، أقساط، آجال طعن، واستشارات متأخرة. */
    @Transactional(readOnly = true)
    public List<UrgentItem> urgent() {
        User me = securityUtils.currentUser();
        boolean all = securityUtils.has(Permission.DASHBOARD_ALL);
        LocalDate today = LocalDate.now();
        List<UrgentItem> out = new ArrayList<>();

        // الجلسات القادمة
        LocalDate hearingTo = today.plusDays(settings.hearingAlertDays());
        Set<Long> myCaseIds = new HashSet<>();
        for (LegalCase c : visibleCases(me, all)) {
            myCaseIds.add(c.getId());
        }
        for (Hearing h : hearingRepo.findByHearingDateBetweenOrderByHearingDateAsc(today, hearingTo)) {
            if (!myCaseIds.contains(h.getCaseId())) continue;
            LegalCase c = caseRepo.findById(h.getCaseId()).orElse(null);
            if (c == null) continue;
            out.add(new UrgentItem("HEARING", "جلسة",
                    "جلسة " + (h.getType() == null ? "" : h.getType().label()) + " — " + c.getCaseNumber(),
                    nz(c.getSubject()), h.getHearingDate(),
                    (int) ChronoUnit.DAYS.between(today, h.getHearingDate()),
                    "info", "CASE", c.getId()));
        }

        // آجال الطعن التي توشك على الانتهاء
        LocalDate appealTo = today.plusDays(settings.appealAlertDays());
        for (LegalCase c : caseRepo.findByAppealDeadlineBetween(today, appealTo)) {
            if (!all && !isMine(c.getAssignedLawyer(), me)) continue;
            if (c.getExecutionFileId() != null) continue;
            int daysLeft = (int) ChronoUnit.DAYS.between(today, c.getAppealDeadline());
            out.add(new UrgentItem("APPEAL_DEADLINE", "أجل طعن",
                    "أجل الطعن ينتهي — القضية " + c.getCaseNumber(),
                    daysLeft == 0
                            ? "اليوم هو آخر يوم في المهلة — التحويل للتنفيذ لا يجوز قبل الغد"
                            : "متبقٍ " + daysLeft + " يوماً",
                    c.getAppealDeadline(), daysLeft,
                    daysLeft <= 2 ? "danger" : "warn", "CASE", c.getId()));
        }

        // الأقساط المستحقة والمتأخرة
        LocalDate instTo = today.plusDays(settings.installmentAlertDays());
        var dueStatuses = List.of(Enums.InstallmentStatus.DUE, Enums.InstallmentStatus.PARTIAL,
                Enums.InstallmentStatus.OVERDUE);
        List<Installment> due = new ArrayList<>();
        due.addAll(installmentRepo.findByDueDateBetweenAndStatusIn(today, instTo, dueStatuses));
        due.addAll(installmentRepo.findByDueDateBeforeAndStatusIn(today, dueStatuses));
        for (Installment inst : due) {
            FinancialFile f = fileRepo.findById(inst.getFinancialFileId()).orElse(null);
            if (f == null) continue;
            if (!all && !isMine(f.getAssignedLawyer(), me)) continue;
            int daysLeft = (int) ChronoUnit.DAYS.between(today, inst.getDueDate());
            out.add(new UrgentItem("INSTALLMENT", "قسط",
                    "القسط رقم " + inst.getSeq() + " — الملف " + f.getFileNumber(),
                    (daysLeft < 0 ? "متأخر " + Math.abs(daysLeft) + " يوماً" : "يستحق خلال " + daysLeft + " يوماً")
                            + " — " + inst.getAmount() + " " + settings.currency(),
                    inst.getDueDate(), daysLeft, daysLeft < 0 ? "danger" : "warn",
                    "FINANCIAL_FILE", f.getId()));
        }

        // الاستشارات المتأخرة عن موعدها
        for (Consultation c : visibleConsultations(me, all)) {
            if (!c.isActiveWorkload() || c.getDueDate() == null) continue;
            if (c.getDueDate().isBefore(today)) {
                out.add(new UrgentItem("CONSULTATION", "استشارة",
                        "استشارة متأخرة — " + c.getConsultationNumber(), nz(c.getSubject()),
                        c.getDueDate(), (int) ChronoUnit.DAYS.between(today, c.getDueDate()),
                        "danger", "CONSULTATION", c.getId()));
            }
        }

        out.sort(Comparator.comparing(UrgentItem::date, Comparator.nullsLast(LocalDate::compareTo)));
        return out;
    }

    // ---------- أداء الفريق ----------

    private List<TeamRow> team() {
        List<TeamRow> rows = new ArrayList<>();
        for (User u : userRepo.findByActiveTrue()) {
            boolean lawyer = u.has(Permission.CASE_MANAGE);
            boolean consultant = u.has(Permission.CONSULT_MANAGE);
            if (!lawyer && !consultant) continue;

            long count = 0;
            List<Long> durations = new ArrayList<>();
            if (lawyer) {
                for (FinancialFile f : fileRepo.findByAssignedLawyer_Id(u.getId())) {
                    count++;
                    if (f.getClosedAt() != null && f.getOpenedAt() != null) {
                        durations.add(ChronoUnit.DAYS.between(f.getOpenedAt(), f.getClosedAt().toLocalDate()));
                    }
                }
                for (LegalCase c : caseRepo.findByAssignedLawyer_Id(u.getId())) {
                    count++;
                    if (c.getClosedAt() != null && c.getOpenedAt() != null) {
                        durations.add(ChronoUnit.DAYS.between(c.getOpenedAt(), c.getClosedAt().toLocalDate()));
                    }
                }
            }
            long activeLoad = 0;
            if (consultant) {
                for (Consultation c : consultRepo.findByConsultant_Id(u.getId())) {
                    count++;
                    if (c.isActiveWorkload()) activeLoad++;
                    if (c.getSignedAt() != null && c.getReceivedAt() != null) {
                        durations.add(ChronoUnit.DAYS.between(c.getReceivedAt(), c.getSignedAt().toLocalDate()));
                    }
                }
            }
            Double avg = durations.isEmpty() ? null
                    : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0) * 10.0) / 10.0;
            rows.add(new TeamRow(u.getId(), u.getFullName(),
                    u.getRole() == null ? null : u.getRole().getNameAr(), count, avg, activeLoad));
        }
        rows.sort(Comparator.comparingLong(TeamRow::files).reversed());
        return rows;
    }

    // ---------- المؤشرات المالية ----------

    private Financials financials(List<FinancialFile> files) {
        BigDecimal claimed = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        for (FinancialFile f : files) {
            claimed = claimed.add(nz(f.getClaimAmount()));
            collected = collected.add(nz(f.getPaidAmount()));
            outstanding = outstanding.add(f.getRemainingAmount());
        }
        double rate = claimed.compareTo(BigDecimal.ZERO) > 0
                ? collected.multiply(BigDecimal.valueOf(100))
                .divide(claimed, 1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        return new Financials(claimed, collected, outstanding, rate, settings.currency());
    }

    // ---------- الرؤية حسب الصلاحية ----------

    private List<FinancialFile> visibleFiles(User me, boolean all) {
        if (all || securityUtils.has(Permission.FINANCIAL_VIEW_ALL)) {
            return fileRepo.findAll();
        }
        return fileRepo.findByAssignedLawyer_Id(me.getId());
    }

    private List<LegalCase> visibleCases(User me, boolean all) {
        if (all || securityUtils.has(Permission.CASE_VIEW_ALL)) {
            return caseRepo.findAll();
        }
        return caseRepo.findByAssignedLawyer_Id(me.getId());
    }

    private List<ExecutionFile> visibleExecutions(User me, boolean all) {
        if (all || securityUtils.has(Permission.EXECUTION_VIEW_ALL)) {
            return execRepo.findAll();
        }
        return execRepo.findByAssignedLawyer_Id(me.getId());
    }

    private List<Consultation> visibleConsultations(User me, boolean all) {
        if (all || securityUtils.has(Permission.CONSULT_VIEW_ALL)) {
            return consultRepo.findAll();
        }
        return consultRepo.findByConsultant_Id(me.getId());
    }

    private static boolean isMine(User u, User me) {
        return u != null && me != null && me.getId().equals(u.getId());
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
