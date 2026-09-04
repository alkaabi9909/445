package com.qanoon.service;

import com.qanoon.common.BusinessException;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.SettingService;
import com.qanoon.domain.*;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** تقارير المكتب: مالي، قضايا، مستشارون، فريق — مع تصدير Excel. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final FinancialFileRepository fileRepo;
    private final LegalCaseRepository caseRepo;
    private final ExecutionFileRepository execRepo;
    private final ConsultationRepository consultRepo;
    private final UserRepository userRepo;
    private final AssignmentEngine assignmentEngine;
    private final SettingService settings;
    private final SecurityUtils securityUtils;

    public record Report(String title, List<String> headers, List<List<Object>> rows,
                         List<DashboardService.ChartPoint> chart, Map<String, Object> totals) {}

    @Transactional(readOnly = true)
    public Report build(String kind, LocalDate from, LocalDate to) {
        securityUtils.require(Permission.REPORTS_VIEW);
        return switch (kind == null ? "" : kind) {
            case "financial" -> financial(from, to);
            case "cases" -> cases(from, to);
            case "consultants" -> consultants();
            case "team" -> team();
            default -> throw new BusinessException("نوع تقرير غير معروف: " + kind);
        };
    }

    // ---------- التقرير المالي ----------

    private Report financial(LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal claimed = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal remaining = BigDecimal.ZERO;
        Map<String, Long> byStatus = new LinkedHashMap<>();

        for (FinancialFile f : fileRepo.findAll()) {
            if (outOfRange(f.getOpenedAt(), from, to)) continue;
            claimed = claimed.add(nz(f.getClaimAmount()));
            collected = collected.add(nz(f.getPaidAmount()));
            remaining = remaining.add(f.getRemainingAmount());
            if (f.getStatus() != null) {
                byStatus.merge(f.getStatus().label(), 1L, Long::sum);
            }
            rows.add(List.of(
                    nz(f.getFileNumber()),
                    f.getClient() == null ? "" : f.getClient().getName(),
                    f.getDebtor() == null ? "" : f.getDebtor().getName(),
                    nz(f.getClaimAmount()),
                    nz(f.getPaidAmount()),
                    f.getRemainingAmount(),
                    f.getStatus() == null ? "" : f.getStatus().label(),
                    f.getAssignedLawyer() == null ? "" : f.getAssignedLawyer().getFullName(),
                    f.getOpenedAt() == null ? "" : f.getOpenedAt()));
        }

        double rate = claimed.compareTo(BigDecimal.ZERO) > 0
                ? collected.doubleValue() * 100.0 / claimed.doubleValue() : 0.0;
        return new Report("التقرير المالي",
                List.of("رقم الملف", "الموكل", "المدين", "المطالبة", "المحصّل", "المتبقي", "الحالة", "المحامي", "تاريخ الفتح"),
                rows, chart(byStatus),
                Map.of("claimed", claimed, "collected", collected, "remaining", remaining,
                        "collectionRate", Math.round(rate * 10.0) / 10.0,
                        "count", rows.size(), "currency", settings.currency()));
    }

    // ---------- تقرير القضايا ----------

    private Report cases(LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        Map<String, Long> byType = new LinkedHashMap<>();
        List<Long> durations = new ArrayList<>();

        for (LegalCase c : caseRepo.findAll()) {
            if (outOfRange(c.getOpenedAt(), from, to)) continue;
            if (c.getCaseType() != null) {
                byType.merge(c.getCaseType().label(), 1L, Long::sum);
            }
            if (c.getClosedAt() != null && c.getOpenedAt() != null) {
                durations.add(ChronoUnit.DAYS.between(c.getOpenedAt(), c.getClosedAt().toLocalDate()));
            }
            rows.add(List.of(
                    nz(c.getCaseNumber()),
                    nz(c.getCourtCaseNumber()),
                    nz(c.getCourt()),
                    c.getCaseType() == null ? "" : c.getCaseType().label(),
                    c.getClient() == null ? "" : c.getClient().getName(),
                    c.getOpponent() == null ? "" : c.getOpponent().getName(),
                    c.getStatus() == null ? "" : c.getStatus().label(),
                    c.getJudgmentDate() == null ? "" : c.getJudgmentDate(),
                    c.getAppealDeadline() == null ? "" : c.getAppealDeadline(),
                    c.getAssignedLawyer() == null ? "" : c.getAssignedLawyer().getFullName()));
        }

        double avg = durations.isEmpty() ? 0
                : durations.stream().mapToLong(Long::longValue).average().orElse(0);
        return new Report("تقرير القضايا",
                List.of("رقم القضية", "رقم المحكمة", "المحكمة", "النوع", "الموكل", "الخصم", "الحالة",
                        "تاريخ الحكم", "أجل الطعن", "المحامي"),
                rows, chart(byType),
                Map.of("count", rows.size(), "avgDays", Math.round(avg * 10.0) / 10.0,
                        "executions", execRepo.count()));
    }

    // ---------- تقرير المستشارين ----------

    private Report consultants() {
        List<List<Object>> rows = new ArrayList<>();
        Map<String, Long> load = new LinkedHashMap<>();

        for (User u : assignmentEngine.candidates()) {
            List<Consultation> mine = consultRepo.findByConsultant_Id(u.getId());
            long active = assignmentEngine.activeCount(u.getId());
            long signed = mine.stream().filter(c -> c.getSignedAt() != null).count();
            List<Long> durations = mine.stream()
                    .filter(c -> c.getSignedAt() != null && c.getReceivedAt() != null)
                    .map(c -> ChronoUnit.DAYS.between(c.getReceivedAt(), c.getSignedAt().toLocalDate()))
                    .toList();
            double avg = durations.isEmpty() ? 0
                    : durations.stream().mapToLong(Long::longValue).average().orElse(0);
            load.put(u.getFullName(), active);
            rows.add(List.of(
                    u.getFullName(),
                    nz(u.getSpecialization()),
                    u.getRole() == null ? "" : u.getRole().getNameAr(),
                    mine.size(),
                    active,
                    signed,
                    Math.round(avg * 10.0) / 10.0,
                    Math.round(assignmentEngine.yearsSince(u.getJoinedAt(), LocalDate.now()) * 10.0) / 10.0));
        }
        return new Report("تقرير المستشارين",
                List.of("المستشار", "التخصص", "الدور", "إجمالي الاستشارات", "الحمل النشط",
                        "الموقّعة", "متوسط المدة (يوم)", "سنوات الخدمة"),
                rows, chart(load), Map.of("count", rows.size()));
    }

    // ---------- تقرير الفريق ----------

    private Report team() {
        List<List<Object>> rows = new ArrayList<>();
        Map<String, Long> byPerson = new LinkedHashMap<>();
        for (User u : userRepo.findByActiveTrue()) {
            long files = fileRepo.findByAssignedLawyer_Id(u.getId()).size();
            long cases = caseRepo.findByAssignedLawyer_Id(u.getId()).size();
            long execs = execRepo.findByAssignedLawyer_Id(u.getId()).size();
            long consults = consultRepo.findByConsultant_Id(u.getId()).size();
            long total = files + cases + execs + consults;
            if (total == 0) continue;
            byPerson.put(u.getFullName(), total);
            rows.add(List.of(
                    u.getFullName(),
                    u.getRole() == null ? "" : u.getRole().getNameAr(),
                    files, cases, execs, consults, total));
        }
        rows.sort((a, b) -> Long.compare(((Number) b.get(6)).longValue(), ((Number) a.get(6)).longValue()));
        return new Report("تقرير الفريق",
                List.of("العضو", "الدور", "ملفات مالية", "قضايا", "تنفيذ", "استشارات", "الإجمالي"),
                rows, chart(byPerson), Map.of("count", rows.size()));
    }

    // ---------- أدوات ----------

    private static List<DashboardService.ChartPoint> chart(Map<String, Long> data) {
        String[] palette = {"#0f2c4c", "#c8a253", "#1b4370", "#0f7b52", "#b45309", "#b91c1c", "#6b7280", "#7c3aed"};
        List<DashboardService.ChartPoint> out = new ArrayList<>();
        int i = 0;
        for (var e : data.entrySet()) {
            out.add(new DashboardService.ChartPoint(e.getKey(), e.getValue(), palette[i++ % palette.length]));
        }
        return out;
    }

    private static boolean outOfRange(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return false;
        if (from != null && d.isBefore(from)) return true;
        return to != null && d.isAfter(to);
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
