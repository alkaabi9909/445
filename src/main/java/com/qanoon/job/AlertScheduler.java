package com.qanoon.job;

import com.qanoon.common.NotificationService;
import com.qanoon.common.SettingService;
import com.qanoon.domain.*;
import com.qanoon.repo.*;
import com.qanoon.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * التنبيهات التلقائية اليومية.
 * كل مهمة معزولة في try/catch حتى لا تُسقط مهمةٌ واحدة بقيةَ المهام.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final LegalCaseRepository caseRepo;
    private final HearingRepository hearingRepo;
    private final InstallmentRepository installmentRepo;
    private final FinancialFileRepository fileRepo;
    private final ConsultationRepository consultRepo;
    private final UserRepository userRepo;
    private final NotificationService notifications;
    private final SettingService settings;
    private final BackupService backupService;

    /** الجولة اليومية: ٧ صباحاً بتوقيت الإمارات. */
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Dubai")
    @Transactional
    public void dailyAlerts() {
        run("تنبيه أجل الطعن", this::appealDeadlineAlerts);
        run("تذكير الجلسات", this::hearingReminders);
        run("الأقساط المستحقة", this::installmentAlerts);
        run("الاستشارات المتأخرة", this::overdueConsultations);
    }

    /** النسخ الاحتياطي التلقائي: ٢ صباحاً. */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Dubai")
    public void nightlyBackup() {
        run("النسخ الاحتياطي التلقائي", () -> backupService.runBackup("تلقائي"));
    }

    // ---------- المهام ----------

    /**
     * ينبّه قبل انتهاء أجل الطعن بالمدة المضبوطة، ويبقى التنبيه قائماً
     * كل يوم حتى اليوم الأخير من المهلة.
     */
    void appealDeadlineAlerts() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(settings.appealAlertDays());
        for (LegalCase c : caseRepo.findByAppealDeadlineBetween(today, until)) {
            if (c.getExecutionFileId() != null || c.getAssignedLawyer() == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, c.getAppealDeadline());
            String message = daysLeft == 0
                    ? "اليوم هو آخر يوم في مهلة الطعن — التحويل للتنفيذ لا يجوز إلا من الغد."
                    : "متبقٍ " + daysLeft + " يوماً على انتهاء أجل الطعن (" + c.getAppealDeadline() + ").";
            notifications.push(c.getAssignedLawyer().getId(), Enums.NotificationType.DEADLINE,
                    "أجل الطعن — القضية " + c.getCaseNumber(), message,
                    "CASE", c.getId(), c.getAppealDeadline(),
                    "APPEAL:" + c.getId() + ":" + today);
        }
    }

    void hearingReminders() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(settings.hearingAlertDays());
        for (Hearing h : hearingRepo.findByHearingDateBetweenOrderByHearingDateAsc(today, until)) {
            LegalCase c = caseRepo.findById(h.getCaseId()).orElse(null);
            if (c == null || c.getAssignedLawyer() == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, h.getHearingDate());
            notifications.push(c.getAssignedLawyer().getId(), Enums.NotificationType.WARNING,
                    "جلسة قادمة — القضية " + c.getCaseNumber(),
                    (daysLeft == 0 ? "الجلسة اليوم" : "الجلسة بعد " + daysLeft + " يوماً")
                            + (h.getCourt() == null ? "" : " — " + h.getCourt()),
                    "CASE", c.getId(), h.getHearingDate(),
                    "HEARING:" + h.getId() + ":" + today);
        }
    }

    /** ينبّه بالأقساط القريبة، ويحدّث حالة الأقساط الفائتة غير المسددة إلى "متأخر". */
    void installmentAlerts() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(settings.installmentAlertDays());
        List<Enums.InstallmentStatus> open = List.of(
                Enums.InstallmentStatus.DUE, Enums.InstallmentStatus.PARTIAL);

        for (Installment inst : installmentRepo.findByDueDateBeforeAndStatusIn(today, open)) {
            inst.setStatus(Enums.InstallmentStatus.OVERDUE);
            installmentRepo.save(inst);
        }

        List<Enums.InstallmentStatus> all = List.of(
                Enums.InstallmentStatus.DUE, Enums.InstallmentStatus.PARTIAL, Enums.InstallmentStatus.OVERDUE);
        for (Installment inst : installmentRepo.findByDueDateBetweenAndStatusIn(today, until, all)) {
            FinancialFile f = fileRepo.findById(inst.getFinancialFileId()).orElse(null);
            if (f == null || f.getAssignedLawyer() == null) {
                continue;
            }
            notifications.push(f.getAssignedLawyer().getId(), Enums.NotificationType.WARNING,
                    "قسط مستحق — الملف " + f.getFileNumber(),
                    "القسط رقم " + inst.getSeq() + " بمبلغ " + inst.getAmount() + " "
                            + settings.currency() + " يستحق في " + inst.getDueDate(),
                    "FINANCIAL_FILE", f.getId(), inst.getDueDate(),
                    "INST:" + inst.getId() + ":" + today);
        }
    }

    void overdueConsultations() {
        LocalDate today = LocalDate.now();
        List<User> seniors = userRepo.findByActiveTrue().stream()
                .filter(u -> u.has(Permission.CONSULT_REVIEW))
                .toList();

        for (Consultation c : consultRepo.findAll()) {
            if (!c.isActiveWorkload() || c.getDueDate() == null || !c.getDueDate().isBefore(today)) {
                continue;
            }
            long late = ChronoUnit.DAYS.between(c.getDueDate(), today);
            String title = "استشارة متأخرة — " + c.getConsultationNumber();
            String msg = "تجاوزت موعدها بـ " + late + " يوماً: " + c.getSubject();

            if (c.getConsultant() != null) {
                notifications.push(c.getConsultant().getId(), Enums.NotificationType.WARNING,
                        title, msg, "CONSULTATION", c.getId(), c.getDueDate(),
                        "CONS:" + c.getId() + ":" + today);
            }
            for (User s : seniors) {
                notifications.push(s.getId(), Enums.NotificationType.WARNING, title, msg,
                        "CONSULTATION", c.getId(), c.getDueDate(),
                        "CONS_SR:" + c.getId() + ":" + s.getId() + ":" + today);
            }
        }
    }

    private void run(String name, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            log.error("فشلت المهمة المجدولة [{}]: {}", name, ex.getMessage(), ex);
        }
    }
}
