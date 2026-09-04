package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * اختبارات إدارة المستخدمين والأدوار: التحقق من المدخلات،
 * حماية الأدوار الأساسية، ومنع حذف دور مرتبط بمستخدمين.
 */
class UserRoleControllerTest extends AbstractControllerTest {

    // ---------- المستخدمون ----------

    @Test
    @DisplayName("قائمة المستخدمين لا تسرّب تجزئة كلمة المرور")
    void userListNeverLeaksPasswordHash() throws Exception {
        String body = bodyOf(mvc.perform(get("/api/users").session(admin()))
                .andExpect(status().isOk())
                .andReturn());
        assertFalse(body.contains("passwordHash"), "قائمة المستخدمين يجب ألا تحوي التجزئة");
        assertTrue(body.contains("admin"), "يجب أن تظهر الحسابات المزروعة");
    }

    @Test
    @DisplayName("إنشاء مستخدم يتحقق من كل حقل مطلوب برسالة تشرح الناقص")
    void createUserValidatesRequiredFields() throws Exception {
        long roleId = anyRoleId("CONSULTANT");

        String[][] cases = {
                {"{\"username\":\"\",\"password\":\"Valid@123\",\"fullName\":\"اسم\",\"roleId\":" + roleId + "}", "اسم المستخدم"},
                {"{\"username\":\"has space\",\"password\":\"Valid@123\",\"fullName\":\"اسم\",\"roleId\":" + roleId + "}", "مسافات"},
                {"{\"username\":\"ok_user\",\"password\":\"Valid@123\",\"fullName\":\"\",\"roleId\":" + roleId + "}", "الاسم الكامل"},
                {"{\"username\":\"ok_user\",\"password\":\"Valid@123\",\"fullName\":\"اسم\"}", "الدور"},
                {"{\"username\":\"ok_user\",\"password\":\"weak\",\"fullName\":\"اسم\",\"roleId\":" + roleId + "}", "كلمة المرور"},
        };

        for (String[] c : cases) {
            MvcResult res = mvc.perform(post("/api/users")
                            .session(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c[0]))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertTrue(errorOf(res).contains(c[1]),
                    "الرسالة يجب أن تذكر [" + c[1] + "] — الفعلية: " + errorOf(res));
        }
    }

    @Test
    @DisplayName("اسم المستخدم المكرر يُرفض")
    void duplicateUsernameIsRejected() throws Exception {
        MvcResult res = mvc.perform(post("/api/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Valid@123\","
                                + "\"fullName\":\"مكرر\",\"roleId\":" + anyRoleId("MANAGER") + "}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مستخدم مسبقاً"), errorOf(res));
    }

    @Test
    @DisplayName("دور غير موجود يعيد 404 لا 500")
    void unknownRoleIdIsNotFound() throws Exception {
        mvc.perform(post("/api/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost_role_user\",\"password\":\"Valid@123\","
                                + "\"fullName\":\"اسم\",\"roleId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("إنشاء مستخدم صالح ينجح ويظهر في القائمة ويستطيع الدخول")
    void createValidUserWorksEndToEnd() throws Exception {
        String username = "new_lawyer_" + System.currentTimeMillis();
        String password = "Fresh@2026";

        mvc.perform(post("/api/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
                                + "\"fullName\":\"محامٍ جديد\",\"roleId\":" + anyRoleId("LAWYER") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        String list = bodyOf(mvc.perform(get("/api/users").session(admin())).andReturn());
        assertTrue(list.contains(username), "المستخدم الجديد يجب أن يظهر في القائمة");

        assertNotNull(freshLogin(username, password), "المستخدم الجديد يجب أن يستطيع الدخول");
    }

    @Test
    @DisplayName("تعيين كلمة مرور جديدة يخضع لنفس سياسة كلمة المرور")
    void resetPasswordEnforcesPolicy() throws Exception {
        long id = userIdOf("consult2");
        MvcResult res = mvc.perform(post("/api/users/" + id + "/reset-password")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertFalse(errorOf(res).isBlank(), "يجب أن تشرح الرسالة سبب رفض كلمة المرور");
    }

    @Test
    @DisplayName("مستخدم غير موجود يعيد 404 على كل العمليات")
    void unknownUserIsNotFound() throws Exception {
        mvc.perform(post("/api/users/999999/unlock").session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/users/999999/reset-password")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Valid@123\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- الأدوار ----------

    @Test
    @DisplayName("قائمة الصلاحيات تعيد الرموز مع أوصافها العربية")
    void permissionCatalogueIsLabelled() throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/roles/permissions").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(arr.isArray() && arr.size() >= 30, "يجب أن تُعاد كل الصلاحيات المعرّفة");
        for (JsonNode p : arr) {
            assertFalse(p.get("code").asText().isBlank(), "لكل صلاحية رمز");
            assertFalse(p.get("label").asText().isBlank(), "لكل صلاحية وصف عربي");
        }
    }

    @Test
    @DisplayName("إنشاء دور يتحقق من الرمز والاسم ووجود صلاحية واحدة على الأقل")
    void createRoleValidatesInput() throws Exception {
        String[][] cases = {
                {"{\"code\":\"\",\"nameAr\":\"اسم\",\"permissions\":[\"CASE_VIEW\"]}", "رمز الدور"},
                {"{\"code\":\"X1\",\"nameAr\":\"\",\"permissions\":[\"CASE_VIEW\"]}", "اسم الدور"},
                {"{\"code\":\"X2\",\"nameAr\":\"اسم\",\"permissions\":[]}", "صلاحية واحدة"},
        };
        for (String[] c : cases) {
            MvcResult res = mvc.perform(post("/api/roles")
                            .session(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c[0]))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertTrue(errorOf(res).contains(c[1]),
                    "الرسالة يجب أن تذكر [" + c[1] + "] — الفعلية: " + errorOf(res));
        }
    }

    @Test
    @DisplayName("الصلاحيات المجهولة تُصفّى ولا تُحفظ")
    void unknownPermissionsAreFilteredOut() throws Exception {
        String code = "FILTER_TEST_" + System.currentTimeMillis();
        MvcResult res = mvc.perform(post("/api/roles")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"nameAr\":\"دور تصفية\","
                                + "\"permissions\":[\"CASE_VIEW\",\"SUPER_ADMIN_GOD_MODE\",\"NOT_A_PERM\"]}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = bodyOf(res);
        assertTrue(body.contains("CASE_VIEW"), "الصلاحية الصحيحة تُحفظ");
        assertFalse(body.contains("SUPER_ADMIN_GOD_MODE"), "الصلاحية المخترعة يجب أن تُرفض صامتاً");
        assertFalse(body.contains("NOT_A_PERM"), "كل رمز غير معروف يُصفّى");
    }

    @Test
    @DisplayName("دور بكل صلاحياته مجهولة يُرفض بدل أن يُحفظ فارغاً")
    void roleWithOnlyUnknownPermissionsIsRejected() throws Exception {
        MvcResult res = mvc.perform(post("/api/roles")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ALL_FAKE\",\"nameAr\":\"دور وهمي\","
                                + "\"permissions\":[\"FAKE_ONE\",\"FAKE_TWO\"]}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("صلاحية واحدة"), errorOf(res));
    }

    @Test
    @DisplayName("الأدوار الأساسية محمية من التعديل والحذف")
    void systemRolesAreProtected() throws Exception {
        long managerRole = anyRoleId("MANAGER");

        MvcResult update = mvc.perform(put("/api/roles/" + managerRole)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameAr\":\"مدير معدّل\",\"permissions\":[\"CASE_VIEW\"]}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(update).contains("أساسي"), errorOf(update));

        MvcResult delete = mvc.perform(delete("/api/roles/" + managerRole).session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(delete).contains("أساسي"), errorOf(delete));
    }

    @Test
    @DisplayName("لا يجوز حذف دور مرتبط بمستخدمين")
    void roleInUseCannotBeDeleted() throws Exception {
        // SECRETARY دور غير أساسي ومرتبط بالمستخدم secretary1
        long secretaryRole = anyRoleId("SECRETARY");
        MvcResult res = mvc.perform(delete("/api/roles/" + secretaryRole).session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مرتبط"),
                "الرسالة يجب أن تذكر ارتباط الدور بمستخدمين — " + errorOf(res));
    }

    @Test
    @DisplayName("دور مخصص بلا مستخدمين يُنشأ ويُعدَّل ويُحذف")
    void customRoleFullLifecycle() throws Exception {
        String code = "TEMP_ROLE_" + System.currentTimeMillis();

        JsonNode created = treeOf(mvc.perform(post("/api/roles")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"nameAr\":\"دور مؤقت\","
                                + "\"permissions\":[\"CASE_VIEW\",\"ARCHIVE_VIEW\"]}"))
                .andExpect(status().isOk()));
        long id = created.get("data").get("id").asLong();

        mvc.perform(put("/api/roles/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameAr\":\"دور مؤقت معدّل\",\"permissions\":[\"CASE_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nameAr").value("دور مؤقت معدّل"));

        mvc.perform(delete("/api/roles/" + id).session(admin()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/roles/" + id).session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("إحصاء استخدام الأدوار يعكس المستخدمين المزروعين")
    void roleUsageCountsSeededUsers() throws Exception {
        JsonNode usage = treeOf(mvc.perform(get("/api/roles/usage").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(usage.size() > 0, "يجب أن يُعاد إحصاء لكل دور مستخدم");
    }

    // ---------- أدوات ----------

    private long anyRoleId(String code) throws Exception {
        JsonNode roles = treeOf(mvc.perform(get("/api/roles").session(admin()))
                .andExpect(status().isOk()));
        for (JsonNode r : roles) {
            if (code.equals(r.get("code").asText())) {
                return r.get("id").asLong();
            }
        }
        throw new AssertionError("الدور " + code + " غير موجود في البيانات التجريبية");
    }

    private long userIdOf(String username) throws Exception {
        JsonNode users = treeOf(mvc.perform(get("/api/users").session(admin()))
                .andExpect(status().isOk()));
        for (JsonNode u : users) {
            if (username.equals(u.get("username").asText())) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError("المستخدم " + username + " غير موجود");
    }
}
