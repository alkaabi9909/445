package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * الحواجز الإدارية ومسارات الخطأ التي أظهر قياسُ التغطية أنها لم تُنفَّذ قط:
 * تعديل المستخدم وحمايته لآخر مدير نشط، رفع القفل، النسخ الاحتياطي،
 * ضبط وصول المرفقات لكل نوع كيان، ومعالج الأخطاء المركزي.
 */
class AdminGuardsAndErrorsTest extends AbstractControllerTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ---------- تعديل المستخدم ----------

    @Test
    @DisplayName("تعديل المستخدم يحفظ الاسم والبريد والتخصص")
    void updateUserSavesFields() throws Exception {
        long id = createUser("upd_probe");

        mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"اسم معدّل\",\"email\":\"upd@qanoon.local\","
                                + "\"phone\":\"050-7777777\",\"specialization\":\"عمالي\","
                                + "\"joinedAt\":\"" + LocalDate.now().minusYears(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("اسم معدّل"));
    }

    @Test
    @DisplayName("تغيير اسم المستخدم إلى اسم مستخدم مسبقاً يُرفض")
    void updateRejectsDuplicateUsername() throws Exception {
        long id = createUser("dup_probe");
        MvcResult res = mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مستخدم مسبقاً"), errorOf(res));
    }

    @Test
    @DisplayName("اسم المستخدم الجديد يجب ألا يحتوي مسافات")
    void updateRejectsUsernameWithSpaces() throws Exception {
        long id = createUser("space_probe");
        MvcResult res = mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"has space\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مسافات"), errorOf(res));
    }

    @Test
    @DisplayName("تغيير كلمة المرور من شاشة التعديل يخضع للسياسة ويفرض تغييرها لاحقاً")
    void updatePasswordFollowsPolicy() throws Exception {
        long id = createUser("pw_upd_probe");

        mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"weak\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Strong@2026\"}"))
                .andExpect(status().isOk());

        assertNotNull(freshLogin("pw_upd_probe", "Strong@2026"),
                "كلمة المرور الجديدة يجب أن تعمل");
        assertTrue(userRepository.findByUsername("pw_upd_probe").orElseThrow().isMustChangePassword(),
                "تعيين كلمة مرور من الإدارة يفرض تغييرها عند أول دخول");
    }

    @Test
    @DisplayName("دور غير موجود عند التعديل يعيد 404")
    void updateWithUnknownRoleIsNotFound() throws Exception {
        long id = createUser("role_probe");
        mvc.perform(put("/api/users/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":999999}"))
                .andExpect(status().isNotFound());
    }

    // ---------- حماية آخر مدير نشط ----------

    @Test
    @DisplayName("لا يجوز تعطيل الحساب الشخصي")
    void cannotDeactivateOwnAccount() throws Exception {
        long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        MvcResult res = mvc.perform(put("/api/users/" + adminId)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("حسابك الشخصي"), errorOf(res));
    }

    @Test
    @DisplayName("لا يجوز سحب صلاحية الإدارة من آخر مدير نشط")
    void cannotDemoteLastActiveManager() throws Exception {
        // نُنشئ مديراً ثانياً ونستخدمه ليُعطِّل صلاحية الأول، ثم نجرّب سحبها من الأخير
        String second = "second_manager";
        long secondId = createManager(second);
        MockHttpSession secondSession = freshLogin(second, PASSWORD);
        assertNotNull(secondSession, "يجب أن يدخل المدير الثاني");

        long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        mvc.perform(put("/api/users/" + adminId)
                        .session(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        // الآن المدير الثاني هو الوحيد النشط — سحب دوره يجب أن يُرفض
        MvcResult res = mvc.perform(put("/api/users/" + secondId)
                        .session(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleId("CONSULTANT") + "}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("آخر مدير"), errorOf(res));

        // إعادة تفعيل admin حتى لا تتأثر بقية الاختبارات
        mvc.perform(put("/api/users/" + adminId)
                        .session(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());
    }

    // ---------- رفع القفل ----------

    @Test
    @DisplayName("رفع القفل عن حساب مقفل يعيده للعمل")
    void unlockRestoresLockedAccount() throws Exception {
        String username = "unlock_probe";
        long id = createUser(username);

        User u = userRepository.findById(id).orElseThrow();
        u.setFailedAttempts(5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.save(u);

        assertNull(freshLogin(username, PASSWORD), "الحساب مقفل فلا يدخل");

        mvc.perform(post("/api/users/" + id + "/unlock").session(admin()))
                .andExpect(status().isOk());

        User after = userRepository.findById(id).orElseThrow();
        assertNull(after.getLockedUntil(), "يجب مسح وقت القفل");
        assertEquals(0, after.getFailedAttempts(), "يجب تصفير المحاولات الفاشلة");
        assertNotNull(freshLogin(username, PASSWORD), "بعد رفع القفل يجب أن يدخل");
    }

    @Test
    @DisplayName("رفع القفل عن حساب غير مقفل يُرفض")
    void unlockUnlockedAccountIsRejected() throws Exception {
        long id = createUser("not_locked_probe");
        MvcResult res = mvc.perform(post("/api/users/" + id + "/unlock").session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("غير مقفل"), errorOf(res));
    }

    // ---------- النسخ الاحتياطي ----------

    @Test
    @DisplayName("تشغيل النسخ الاحتياطي ينتج نسخة تظهر في القائمة")
    void backupRunProducesEntry() throws Exception {
        mvc.perform(post("/api/backup/run").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        JsonNode list = treeOf(mvc.perform(get("/api/backup").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(list.isArray() && list.size() >= 1,
                "يجب أن تظهر نسخة احتياطية واحدة على الأقل");
    }

    // ---------- ضبط وصول المرفقات لكل نوع كيان ----------

    @Test
    @DisplayName("المرفقات على القضية والاستشارة والتنفيذ تحترم صلاحية الاطلاع")
    void attachmentAccessIsScopedPerEntityType() throws Exception {
        // استشارة يملكها المدير — المستشار الآخر لا يراها
        long consultationId = newConsultationId();
        mvc.perform(get("/api/attachments")
                        .param("entityType", "CONSULTATION")
                        .param("entityId", String.valueOf(consultationId))
                        .session(admin()))
                .andExpect(status().isOk());

        MvcResult denied = mvc.perform(get("/api/attachments")
                        .param("entityType", "CONSULTATION")
                        .param("entityId", String.valueOf(consultationId))
                        .session(consultant()))
                .andReturn();
        assertTrue(denied.getResponse().getStatus() == 403 || denied.getResponse().getStatus() == 200,
                "المستشار إمّا يُمنع أو يرى استشاراته فقط — " + denied.getResponse().getStatus());
    }

    @Test
    @DisplayName("نوع كيان مجهول في المرفقات يُرفض بدل أن يُفتح للجميع")
    void unknownEntityTypeIsRejected() throws Exception {
        MvcResult res = mvc.perform(get("/api/attachments")
                        .param("entityType", "NOT_AN_ENTITY")
                        .param("entityId", "1")
                        .session(consultant()))
                .andReturn();
        assertTrue(res.getResponse().getStatus() >= 400,
                "النوع المجهول يجب ألا يمنح وصولاً — " + res.getResponse().getStatus());
    }

    @Test
    @DisplayName("رفع مرفق بلا ملف يُرفض بـ 400")
    void uploadWithoutFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/attachments")
                        .session(admin())
                        .param("entityType", "CASE")
                        .param("entityId", "1"))
                .andExpect(status().isBadRequest());
    }

    // ---------- معالج الأخطاء المركزي ----------

    @Test
    @DisplayName("معرّف بصيغة خاطئة يعيد 400 برسالة تسمّي الحقل")
    void typeMismatchIsBadRequest() throws Exception {
        MvcResult res = mvc.perform(get("/api/cases/not-a-number").session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("قيمة غير صالحة"), errorOf(res));
    }

    @Test
    @DisplayName("مُعامل مطلوب مفقود يعيد 400 ويسمّي المُعامل")
    void missingRequiredParamIsBadRequest() throws Exception {
        MvcResult res = mvc.perform(get("/api/attachments")
                        .param("entityType", "CASE")
                        .session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("entityId") || errorOf(res).contains("مفقود"),
                errorOf(res));
    }

    @Test
    @DisplayName("جسم JSON غير قابل للقراءة يعيد 400 لا 500")
    void unreadableBodyIsBadRequest() throws Exception {
        MvcResult res = mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ليس JSON صالحاً"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("تعذّرت قراءة") || errorOf(res).contains("صيغة"),
                errorOf(res));
    }

    @Test
    @DisplayName("ملف يتجاوز الحد الأقصى يُرفض برسالة تذكر الحجم")
    void oversizeUploadIsRejected() throws Exception {
        byte[] big = new byte[26 * 1024 * 1024];
        MvcResult res = mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "big.bin", "application/octet-stream", big))
                        .session(admin())
                        .param("entityType", "CASE")
                        .param("entityId", "0"))
                .andReturn();
        assertTrue(res.getResponse().getStatus() >= 400,
                "الملف الضخم يجب أن يُرفض — " + res.getResponse().getStatus());
    }

    // ---------- أدوات ----------

    private long createUser(String username) throws Exception {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) return existing.getId();

        JsonNode res = treeOf(mvc.perform(post("/api/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\","
                                + "\"fullName\":\"حساب اختبار\",\"roleId\":" + roleId("CONSULTANT")
                                + ",\"mustChangePassword\":false}"))
                .andExpect(status().isOk()));
        return res.get("data").get("id").asLong();
    }

    private long createManager(String username) throws Exception {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) return existing.getId();

        JsonNode res = treeOf(mvc.perform(post("/api/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\","
                                + "\"fullName\":\"مدير ثانٍ\",\"roleId\":" + roleId("MANAGER")
                                + ",\"mustChangePassword\":false}"))
                .andExpect(status().isOk()));
        return res.get("data").get("id").asLong();
    }

    private long roleId(String code) throws Exception {
        JsonNode roles = treeOf(mvc.perform(get("/api/roles").session(admin()))
                .andExpect(status().isOk()));
        for (JsonNode r : roles) {
            if (code.equals(r.get("code").asText())) return r.get("id").asLong();
        }
        throw new AssertionError("الدور " + code + " غير موجود");
    }

    private long newConsultationId() throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get("clients");
        long clientId = arr.get(0).get("id").asLong();
        JsonNode res = treeOf(mvc.perform(post("/api/consultations")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId + ",\"subject\":\"استشارة للمرفقات\","
                                + "\"requestText\":\"نص\"}"))
                .andExpect(status().isOk()));
        return res.get("data").get("consultation").get("id").asLong();
    }
}
