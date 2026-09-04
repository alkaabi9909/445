package com.qanoon.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * مصفوفة الصلاحيات: تتحقق أن كل مسار محمي فعلاً على الخادم،
 * وأن إخفاء الزر في الواجهة ليس هو الحاجز الوحيد.
 */
class PermissionMatrixTest extends AbstractControllerTest {

    /** المسارات التي يجب أن تُرفض دائماً بلا جلسة. */
    private static final List<String> PROTECTED_GETS = List.of(
            "/api/dashboard",
            "/api/dashboard/urgent",
            "/api/lookups",
            "/api/cases",
            "/api/executions",
            "/api/consultations",
            "/api/financial",
            "/api/archive",
            "/api/laws",
            "/api/parties",
            "/api/users",
            "/api/roles",
            "/api/roles/permissions",
            "/api/settings",
            "/api/audit",
            "/api/notifications",
            "/api/notifications/count",
            "/api/backup",
            "/api/reports/financial",
            "/api/search"
    );

    // ---------- بلا مصادقة ----------

    @ParameterizedTest(name = "بلا جلسة: {0} يعيد 401")
    @ValueSource(strings = {
            "/api/dashboard", "/api/lookups", "/api/cases", "/api/executions",
            "/api/consultations", "/api/financial", "/api/archive", "/api/laws",
            "/api/parties", "/api/users", "/api/roles", "/api/settings",
            "/api/audit", "/api/notifications", "/api/backup", "/api/reports/financial"
    })
    void anonymousIsRejectedEverywhere(String path) throws Exception {
        MvcResult res = mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertFalse(errorOf(res).isBlank(), "رفض 401 يجب أن يحمل رسالة عربية");
    }

    @Test
    @DisplayName("عمليات التعديل بلا جلسة تُرفض أيضاً وليس القراءة فقط")
    void anonymousCannotWrite() throws Exception {
        mvc.perform(post("/api/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"دخيل\",\"kind\":\"CLIENT\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"office.name\":\"مخترق\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/backup/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("المسارات العامة تبقى مفتوحة بلا جلسة")
    void publicPathsStayOpen() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    // ---------- شاشات الإدارة ----------

    @ParameterizedTest(name = "المستشار ممنوع من {0}")
    @ValueSource(strings = {
            "/api/settings", "/api/users", "/api/roles", "/api/roles/permissions",
            "/api/audit", "/api/backup", "/api/roles/usage"
    })
    void consultantIsBlockedFromAdminScreens(String path) throws Exception {
        MvcResult res = mvc.perform(get(path).session(consultant()))
                .andExpect(status().isForbidden())
                .andReturn();
        assertTrue(errorOf(res).contains("صلاحية"),
                "رسالة 403 يجب أن تشرح نقص الصلاحية — " + errorOf(res));
    }

    @ParameterizedTest(name = "المحامي ممنوع من {0}")
    @ValueSource(strings = {"/api/settings", "/api/users", "/api/roles", "/api/audit", "/api/backup"})
    void lawyerIsBlockedFromAdminScreens(String path) throws Exception {
        mvc.perform(get(path).session(lawyer())).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "السكرتير ممنوع من {0}")
    @ValueSource(strings = {"/api/settings", "/api/users", "/api/roles", "/api/audit", "/api/backup"})
    void secretaryIsBlockedFromAdminScreens(String path) throws Exception {
        mvc.perform(get(path).session(secretary())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("المدير وحده يصل لكل شاشات الإدارة")
    void managerReachesAdminScreens() throws Exception {
        for (String path : List.of("/api/settings", "/api/users", "/api/roles",
                "/api/roles/permissions", "/api/roles/usage", "/api/audit", "/api/backup")) {
            mvc.perform(get(path).session(admin()))
                    .andExpect(status().isOk());
        }
    }

    // ---------- الكتابة المحمية ----------

    @Test
    @DisplayName("المستشار لا يستطيع تعديل الإعدادات ولا إنشاء مستخدم")
    void consultantCannotWriteAdminData() throws Exception {
        mvc.perform(put("/api/settings")
                        .session(consultant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"office.name\":\"محاولة\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/users")
                        .session(consultant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"Aa1@aaaa\",\"fullName\":\"س\",\"roleId\":1}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/backup/run").session(consultant()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("المستشار لا يملك صلاحية التقارير بينما كبير المستشارين يملكها")
    void reportsFollowPermissionNotRole() throws Exception {
        mvc.perform(get("/api/reports/financial").session(consultant()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/consultants").session(senior()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("المحامي لا يملك التوقيع النهائي على الآراء")
    void lawyerCannotSignOpinions() throws Exception {
        MvcResult res = mvc.perform(post("/api/consultations/1/sign").session(lawyer()))
                .andReturn();
        assertTrue(res.getResponse().getStatus() == 403 || res.getResponse().getStatus() == 404,
                "المحامي يجب أن يُمنع (403) أو ألا يرى الاستشارة أصلاً (404) — "
                        + res.getResponse().getStatus());
    }

    // ---------- سلامة الرفض ----------

    @Test
    @DisplayName("كل حالات الرفض تعيد JSON موحّداً لا صفحة دخول")
    void rejectionsAreJsonNotRedirects() throws Exception {
        for (String path : PROTECTED_GETS) {
            MvcResult res = mvc.perform(get(path)).andReturn();
            int status = res.getResponse().getStatus();
            assertEquals(401, status, path + " يجب أن يعيد 401 لا " + status);
            String contentType = String.valueOf(res.getResponse().getContentType());
            assertTrue(contentType.contains("json"),
                    path + " يجب أن يعيد JSON لا " + contentType);
        }
    }

    @Test
    @DisplayName("انتهاء الجلسة يعيد 401 وليس 500")
    void invalidatedSessionIsUnauthorized() throws Exception {
        MockHttpSession s = freshLogin("secretary1", PASSWORD);
        assertNotNull(s);
        mvc.perform(get("/api/dashboard").session(s)).andExpect(status().isOk());
        s.invalidate();

        MockHttpSession dead = new MockHttpSession();
        mvc.perform(get("/api/dashboard").session(dead)).andExpect(status().isUnauthorized());
    }
}
