package com.qanoon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qanoon.domain.Enums;
import com.qanoon.repo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * اختبار شامل يتحقق من أن النظام يعمل فعلياً من طرف إلى طرف:
 * الدخول، الصلاحيات، لوحة المعلومات، بوابة الشروط الستة،
 * قفل الرأي بعد التوقيع، وبوابة الاستيفاء.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:qanoon-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "qanoon.storage-path=./target/test-uploads",
        "qanoon.backup-path=./target/test-backups"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QanoonEndToEndTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired LegalCaseRepository caseRepo;
    @Autowired ConsultationRepository consultRepo;
    @Autowired ExecutionFileRepository execRepo;
    @Autowired FinancialFileRepository fileRepo;

    private static MockHttpSession adminSession;

    // ---------- الدخول ----------

    @Test @Order(1)
    void seederLoadedDemoData() {
        assertTrue(userRepo.count() >= 7, "يجب أن تُزرع حسابات المستخدمين");
        assertTrue(fileRepo.count() >= 8, "يجب أن تُزرع الملفات المالية");
        assertTrue(caseRepo.count() >= 8, "يجب أن تُزرع القضايا");
        assertTrue(consultRepo.count() >= 8, "يجب أن تُزرع الاستشارات");
    }

    @Test @Order(2)
    void loginFailsWithWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(3)
    void adminCanLogIn() throws Exception {
        adminSession = new MockHttpSession();
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Qanoon@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(body.toString().contains("admin"), "الرد يجب أن يحوي بيانات المستخدم");
    }

    @Test @Order(4)
    void meReturnsPermissions() throws Exception {
        MvcResult res = mvc.perform(get("/api/auth/me").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("USERS_MANAGE"), "المدير يملك كل الصلاحيات");
        assertTrue(body.contains("CASE_TRANSFER"));
    }

    @Test @Order(5)
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    // ---------- الشاشات الرئيسية ----------

    @Test @Order(6)
    void dashboardAndLookupsWork() throws Exception {
        mvc.perform(get("/api/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload").isArray())
                .andExpect(jsonPath("$.financials").exists());

        mvc.perform(get("/api/lookups").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileStatus").isArray())
                .andExpect(jsonPath("$.orderType").isArray());
    }

    @Test @Order(7)
    void listEndpointsWork() throws Exception {
        for (String path : new String[]{"/api/financial", "/api/cases",
                "/api/executions", "/api/consultations", "/api/archive", "/api/laws"}) {
            mvc.perform(get(path).session(adminSession))
                    .andExpect(status().isOk());
        }
    }

    // ---------- بوابة التحويل: الشروط الستة ----------

    @Test @Order(8)
    void transferBlockedWhenAppealPending() throws Exception {
        var appealed = caseRepo.findByStatus(Enums.CaseStatus.APPEALED);
        assertFalse(appealed.isEmpty(), "يجب أن توجد قضية باستئناف معلّق في البيانات التجريبية");
        Long id = appealed.get(0).getId();

        MvcResult res = mvc.perform(get("/api/cases/" + id + "/transfer-check").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andReturn();
        assertTrue(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("استئناف"),
                "يجب أن يُذكر الاستئناف ضمن الموانع");

        // ومحاولة التحويل الفعلي تُرفض على الخادم
        mvc.perform(post("/api/cases/" + id + "/transfer").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(9)
    void transferBlockedOnLastDayOfAppealPeriod() throws Exception {
        // القضية التي أجل الطعن فيها ينتهي اليوم بالضبط
        var todays = caseRepo.findAll().stream()
                .filter(c -> LocalDate.now().equals(c.getAppealDeadline()))
                .filter(c -> c.getExecutionFileId() == null)
                .filter(c -> !c.isJudgmentFinal())
                .toList();
        assertFalse(todays.isEmpty(), "يجب أن توجد قضية أجل الطعن فيها ينتهي اليوم");

        MvcResult res = mvc.perform(get("/api/cases/" + todays.get(0).getId() + "/transfer-check")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andReturn();
        assertTrue(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("الطعن"),
                "المانع يجب أن يشير لأجل الطعن — اليوم الأخير لا يزال ضمن المهلة");
    }

    @Test @Order(10)
    void transferAllowedWhenJudgmentFinalAndNoAppeal() throws Exception {
        var ready = caseRepo.findAll().stream()
                .filter(c -> c.isJudgmentFinal())
                .filter(c -> c.getExecutionFileId() == null)
                .filter(c -> c.getStatus() != Enums.CaseStatus.CLOSED)
                .filter(c -> c.getJudgmentFor() == Enums.JudgmentFor.CLIENT
                        || c.getJudgmentFor() == Enums.JudgmentFor.PARTIAL)
                .toList();
        assertFalse(ready.isEmpty(), "يجب أن توجد قضية مستوفية للشروط الستة");
        Long id = ready.get(0).getId();

        mvc.perform(get("/api/cases/" + id + "/transfer-check").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        // التحويل الفعلي ينشئ ملف تنفيذ
        long before = execRepo.count();
        mvc.perform(post("/api/cases/" + id + "/transfer").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionNumber").exists());
        assertEquals(before + 1, execRepo.count(), "يجب أن يُنشأ ملف تنفيذ واحد");

        // ولا يجوز تكرار التحويل
        mvc.perform(post("/api/cases/" + id + "/transfer").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    // ---------- بوابة الاستيفاء ----------

    @Test @Order(11)
    void satisfactionBlockedWhileBalanceRemains() throws Exception {
        var open = execRepo.findAll().stream()
                .filter(e -> e.getStatus() != Enums.ExecStatus.CLOSED)
                .filter(e -> e.getRemainingAmount().signum() > 0)
                .toList();
        assertFalse(open.isEmpty(), "يجب أن يوجد ملف تنفيذ عليه متبقٍ");
        Long id = open.get(0).getId();

        mvc.perform(get("/api/executions/" + id + "/satisfaction-check").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));

        mvc.perform(post("/api/executions/" + id + "/satisfy").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    // ---------- قفل الرأي بعد التوقيع ----------

    @Test @Order(12)
    void signedOpinionIsLockedEvenForManager() throws Exception {
        var signed = consultRepo.findAll().stream()
                .filter(c -> c.isLocked() && c.getStatus() == Enums.ConsultStatus.SIGNED)
                .toList();
        assertFalse(signed.isEmpty(), "يجب أن يوجد رأي موقّع ومقفل");
        Long id = signed.get(0).getId();

        // المدير نفسه لا يستطيع تعديل الرأي الموقّع
        mvc.perform(post("/api/consultations/" + id + "/opinion")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinionText\":\"محاولة تعديل\"}"))
                .andExpect(status().isBadRequest());

        // والتصحيح يكون برأي جديد
        mvc.perform(post("/api/consultations/" + id + "/correction").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationNumber").exists());
    }

    @Test @Order(13)
    void consultantCannotReviewOwnOpinion() throws Exception {
        var underReview = consultRepo.findByStatus(Enums.ConsultStatus.UNDER_REVIEW);
        Assumptions.assumeFalse(underReview.isEmpty(), "لا توجد استشارة قيد المراجعة");
        var c = underReview.get(0);
        Assumptions.assumeTrue(c.getConsultant() != null);

        MockHttpSession authorSession = login(c.getConsultant().getUsername());
        Assumptions.assumeTrue(authorSession != null);

        mvc.perform(post("/api/consultations/" + c.getId() + "/review")
                        .session(authorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"اعتماد ذاتي\"}"))
                .andExpect(status().is4xxClientError());
    }

    // ---------- التوزيع الذكي ----------

    @Test @Order(14)
    void smartAssignmentRanksCandidates() throws Exception {
        MvcResult res = mvc.perform(get("/api/consultations/suggest")
                        .param("specialization", "تجاري")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = json.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(arr.isArray() && arr.size() > 0, "يجب أن يعيد مرشحين");

        JsonNode top = arr.get(0);
        assertTrue(top.get("score").asDouble() > 0, "درجة المرشح الأول يجب أن تكون موجبة");
        assertTrue(top.get("score").asDouble() <= 100.0001, "الدرجة لا تتجاوز 100");
        // الترتيب تنازلي
        for (int i = 1; i < arr.size(); i++) {
            assertTrue(arr.get(i - 1).get("score").asDouble() >= arr.get(i).get("score").asDouble(),
                    "المرشحون مرتبون تنازلياً بالدرجة");
        }
        assertTrue(top.get("reason").asText().length() > 0, "سبب الإسناد موثّق نصاً");
    }

    // ---------- الصلاحيات ----------

    @Test @Order(15)
    void consultantCannotReachSettings() throws Exception {
        MockHttpSession s = login("consult1");
        Assumptions.assumeTrue(s != null);
        mvc.perform(get("/api/settings").session(s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").session(s)).andExpect(status().isForbidden());
    }

    @Test @Order(16)
    void accountLocksAfterFailedAttempts() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"lawyer2\",\"password\":\"bad\"}"));
        }
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lawyer2\",\"password\":\"Qanoon@123\"}"))
                .andReturn();
        assertTrue(res.getResponse().getStatus() >= 400,
                "الحساب يجب أن يكون مقفلاً بعد المحاولات الفاشلة");
        assertTrue(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("مقفل")
                        || res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("موقوف"),
                "الرسالة يجب أن تشرح سبب المنع بالعربية");
    }

    // ---------- أدوات ----------

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession s = new MockHttpSession();
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Qanoon@123\"}"))
                .andReturn();
        return r.getResponse().getStatus() == 200 ? s : null;
    }
}
