package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * اختبارات متحكم المصادقة: الدخول، الخروج، /me، تغيير كلمة المرور،
 * سياسة كلمة المرور، وقفل الحساب بعد المحاولات الفاشلة.
 */
class AuthControllerTest extends AbstractControllerTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ---------- الدخول ----------

    @Test
    @DisplayName("الدخول الصحيح يعيد بيانات المستخدم وصلاحياته")
    void loginReturnsUserAndPermissions() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn();

        assertFalse(bodyOf(res).contains("passwordHash"),
                "الرد يجب ألا يسرّب تجزئة كلمة المرور");
    }

    @Test
    @DisplayName("كلمة مرور خاطئة تُرفض برسالة عربية بلا كشف عن وجود الحساب")
    void loginWithWrongPasswordIsRejected() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("consult2", "WrongPass@1")))
                .andExpect(status().is4xxClientError())
                .andReturn();
        assertFalse(errorOf(res).isBlank(), "يجب أن تُرجع رسالة خطأ عربية");
    }

    @Test
    @DisplayName("حساب غير موجود يُرفض بنفس شكل الرفض")
    void loginWithUnknownUserIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("لا-يوجد-هذا-الحساب", "Whatever@1")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("جسم دخول ناقص يُرفض بـ 4xx وليس بخطأ خادم")
    void loginWithMissingFieldsIsClientError() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        int status = res.getResponse().getStatus();
        assertTrue(status >= 400 && status < 500,
                "الجسم الناقص يجب أن يكون خطأ عميل لا خطأ خادم — الحالة الفعلية " + status);
    }

    @Test
    @DisplayName("جسم غير صالح كـ JSON يُرفض بـ 400")
    void loginWithMalformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ليس JSON"))
                .andExpect(status().isBadRequest());
    }

    // ---------- /me ----------

    @Test
    @DisplayName("/me يعيد الصلاحيات وإعدادات المكتب للمستخدم الحالي")
    void meReturnsPermissionsAndSettings() throws Exception {
        JsonNode body = treeOf(mvc.perform(get("/api/auth/me").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.settings.currency").exists()));

        assertTrue(body.get("permissions").isArray());
        assertTrue(body.get("permissions").size() >= 30,
                "المدير يملك كل الصلاحيات المعرّفة في النظام");
        assertEquals("AED", body.get("settings").get("currency").asText(),
                "العملة الافتراضية للمكتب درهم إماراتي");
    }

    @Test
    @DisplayName("/me بلا جلسة يعيد 401 بجسم JSON عربي")
    void meWithoutSessionIsUnauthorized() throws Exception {
        MvcResult res = mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertTrue(errorOf(res).contains("الدخول"),
                "رسالة 401 يجب أن تشرح ضرورة تسجيل الدخول");
    }

    @Test
    @DisplayName("المستشار يرى صلاحياته المحدودة فقط في /me")
    void consultantSeesOnlyItsOwnPermissions() throws Exception {
        String body = bodyOf(mvc.perform(get("/api/auth/me").session(consultant()))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(body.contains("CONSULT_VIEW"), "المستشار يملك عرض الاستشارات");
        assertFalse(body.contains("USERS_MANAGE"), "المستشار لا يملك إدارة المستخدمين");
        assertFalse(body.contains("SETTINGS_MANAGE"), "المستشار لا يملك إدارة الإعدادات");
    }

    // ---------- الخروج ----------

    @Test
    @DisplayName("الخروج يُبطل الجلسة فلا تعود صالحة بعده")
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession s = freshLogin("consult2", PASSWORD);
        assertNotNull(s, "يجب أن ينجح الدخول أولاً");

        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
    }

    // ---------- تغيير كلمة المرور ----------

    @Test
    @DisplayName("تغيير كلمة المرور يرفض كلمة حالية خاطئة")
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/change-password")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"NotMine@1\",\"newPassword\":\"BrandNew@2\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("الحالية"),
                "الرسالة يجب أن تشير إلى كلمة المرور الحالية");
    }

    @Test
    @DisplayName("سياسة كلمة المرور تُطبَّق على كل شرط ناقص")
    void changePasswordEnforcesEachPolicyRule() throws Exception {
        // كل حالة: كلمة مرور مخالفة لشرط واحد، والرسالة يجب أن تشرحه
        String[][] cases = {
                {"Ab@1",              "قصيرة"},
                {"alllower@1",        "كبير"},
                {"ALLUPPER@1",        "صغير"},
                {"NoDigitsHere@",     "رقم"},
                {"NoSpecialChar1",    "رمز"},
                {"With Space@1a",     "مسافات"},
        };
        for (String[] c : cases) {
            MvcResult res = mvc.perform(post("/api/auth/change-password")
                            .session(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + c[0] + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertTrue(errorOf(res).contains(c[1]),
                    "كلمة المرور [" + c[0] + "] يجب أن تُرفض برسالة تذكر: " + c[1]
                            + " — الرسالة الفعلية: " + errorOf(res));
        }
    }

    @Test
    @DisplayName("كلمة المرور الجديدة يجب أن تختلف عن الحالية")
    void changePasswordRejectsSamePassword() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/change-password")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("تختلف"),
                "يجب رفض إعادة استخدام نفس كلمة المرور");
    }

    @Test
    @DisplayName("تغيير كلمة المرور بنجاح يُفعّل الكلمة الجديدة ويُلغي القديمة")
    void changePasswordSwitchesCredentials() throws Exception {
        // حساب مخصّص لهذا الاختبار حتى لا نُفسد كلمات مرور بقية الاختبارات
        String username = "pwd_probe";
        User probe = createProbeUser(username, PASSWORD);
        MockHttpSession s = freshLogin(username, PASSWORD);
        assertNotNull(s, "يجب أن ينجح الدخول بكلمة المرور الأولى");

        String newPassword = "Rotated@2026";
        mvc.perform(post("/api/auth/change-password")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        assertNull(freshLogin(username, PASSWORD), "كلمة المرور القديمة يجب ألا تعمل بعد التغيير");
        assertNotNull(freshLogin(username, newPassword), "كلمة المرور الجديدة يجب أن تعمل");

        User reloaded = userRepository.findById(probe.getId()).orElseThrow();
        assertFalse(reloaded.isMustChangePassword(),
                "بعد التغيير يجب رفع علامة إجبار تغيير كلمة المرور");
    }

    @Test
    @DisplayName("تغيير كلمة المرور بلا جلسة يعيد 401")
    void changePasswordRequiresSession() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- قفل الحساب ----------

    @Test
    @DisplayName("الحساب يُقفل بعد تجاوز حد المحاولات الفاشلة ولو صحّت كلمة المرور بعدها")
    void accountLocksAfterTooManyFailures() throws Exception {
        String username = "lock_probe";
        createProbeUser(username, PASSWORD);

        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(username, "Wrong@" + i)));
        }

        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andReturn();

        assertTrue(res.getResponse().getStatus() >= 400,
                "كلمة المرور الصحيحة يجب أن تُرفض ما دام الحساب مقفلاً");
        String error = errorOf(res);
        assertTrue(error.contains("مقفل") || error.contains("موقوف") || error.contains("قفل"),
                "الرسالة يجب أن تشرح أن الحساب مقفل — الرسالة الفعلية: " + error);

        User locked = userRepository.findByUsername(username).orElseThrow();
        assertNotNull(locked.getLockedUntil(), "يجب أن يُسجَّل وقت انتهاء القفل");
    }

    @Test
    @DisplayName("الحساب المعطّل لا يستطيع الدخول")
    void inactiveUserCannotLogIn() throws Exception {
        String username = "inactive_probe";
        User u = createProbeUser(username, PASSWORD);
        u.setActive(false);
        userRepository.save(u);

        assertNull(freshLogin(username, PASSWORD), "الحساب المعطّل يجب ألا يدخل");
    }

    // ---------- أدوات ----------

    /** ينشئ حساباً تجريبياً مباشرة عبر المستودع (لا عبر الواجهة) بدور المدير. */
    private User createProbeUser(String username, String password) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setFullName("حساب اختبار " + username);
            u.setRole(userRepository.findByUsername("consult1").orElseThrow().getRole());
            u.setActive(true);
            u.setMustChangePassword(true);
            u.setFailedAttempts(0);
            return userRepository.save(u);
        });
    }
}
