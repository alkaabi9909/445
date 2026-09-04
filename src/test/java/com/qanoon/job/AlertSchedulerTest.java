package com.qanoon.job;

import com.qanoon.repo.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * المهام المجدولة: التنبيهات اليومية والنسخ الاحتياطي الليلي.
 *
 * <p>يقع الاختبار في حزمة {@code com.qanoon.job} عمداً لأن مهام التنبيه
 * الفردية معرّفة بمستوى الحزمة، فلا حاجة لتوسيع رؤيتها في كود الإنتاج
 * لمجرد اختبارها.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:qanoon-job-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "qanoon.storage-path=./target/test-uploads",
        "qanoon.backup-path=./target/test-backups"
})
class AlertSchedulerTest {

    @Autowired AlertScheduler scheduler;
    @Autowired NotificationRepository notificationRepository;

    @Test
    @DisplayName("الجولة اليومية تمرّ على كل المهام بلا استثناء")
    void dailyAlertsRunCleanly() {
        assertDoesNotThrow(() -> scheduler.dailyAlerts(),
                "الجولة اليومية يجب أن تكتمل دون أن تنهار");
    }

    @Test
    @DisplayName("كل مهمة تنبيه تعمل منفردة")
    void eachAlertTaskRunsIndividually() {
        assertDoesNotThrow(() -> scheduler.appealDeadlineAlerts(), "تنبيه أجل الطعن");
        assertDoesNotThrow(() -> scheduler.hearingReminders(), "تذكير الجلسات");
        assertDoesNotThrow(() -> scheduler.installmentAlerts(), "الأقساط المستحقة");
        assertDoesNotThrow(() -> scheduler.overdueConsultations(), "الاستشارات المتأخرة");
    }

    @Test
    @DisplayName("الجولة اليومية تُنتج تنبيهات فعلية على البيانات التجريبية")
    void dailyAlertsProduceNotifications() {
        notificationRepository.deleteAll();
        scheduler.dailyAlerts();
        assertTrue(notificationRepository.count() > 0,
                "البيانات التجريبية تتضمن جلسات وأقساطاً وآجال طعن قريبة فيجب أن تُنتج تنبيهات");
    }

    @Test
    @DisplayName("تكرار الجولة في اليوم نفسه لا يضاعف التنبيهات")
    void repeatedRunDoesNotDuplicateNotifications() {
        notificationRepository.deleteAll();
        scheduler.dailyAlerts();
        long afterFirst = notificationRepository.count();

        scheduler.dailyAlerts();
        long afterSecond = notificationRepository.count();

        assertEquals(afterFirst, afterSecond,
                "مفتاح إزالة التكرار يجب أن يمنع تكرار التنبيه نفسه في اليوم نفسه");
    }

    @Test
    @DisplayName("النسخ الاحتياطي الليلي يعمل ولا يرمي استثناءً")
    void nightlyBackupRuns() {
        assertDoesNotThrow(() -> scheduler.nightlyBackup());
    }
}
