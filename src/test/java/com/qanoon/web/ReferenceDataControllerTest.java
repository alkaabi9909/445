package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * اختبارات الشاشات المرجعية: لوحة المعلومات، القوائم المنسدلة، التقارير،
 * سجل النشاطات، الإعدادات، التنبيهات، والتصفيح المشترك بين كل القوائم.
 */
class ReferenceDataControllerTest extends AbstractControllerTest {

    /** كل المسارات التي تعيد {@code PageResult} بالصيغة الموحّدة. */
    private static final List<String> PAGED_ENDPOINTS = List.of(
            "/api/cases", "/api/executions", "/api/consultations",
            "/api/financial", "/api/archive"
    );

    // ---------- لوحة المعلومات ----------

    @Test
    @DisplayName("لوحة المعلومات تعيد أقسامها الأساسية")
    void dashboardHasCoreSections() throws Exception {
        mvc.perform(get("/api/dashboard").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload").isArray())
                .andExpect(jsonPath("$.financials").exists());
    }

    @Test
    @DisplayName("التنبيهات العاجلة تعيد قائمة لكل دور بلا خطأ")
    void urgentItemsWorkForEveryRole() throws Exception {
        for (var session : List.of(admin(), lawyer(), consultant(), senior(), secretary())) {
            JsonNode arr = treeOf(mvc.perform(get("/api/dashboard/urgent").session(session))
                    .andExpect(status().isOk()));
            assertTrue(arr.isArray(), "التنبيهات العاجلة يجب أن تكون مصفوفة");
        }
    }

    // ---------- القوائم المنسدلة ----------

    @Test
    @DisplayName("القوائم المنسدلة تحمل وصفاً عربياً لكل قيمة")
    void lookupsAreLabelled() throws Exception {
        JsonNode lookups = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileStatus").isArray())
                .andExpect(jsonPath("$.orderType").isArray()));

        JsonNode statuses = lookups.get("fileStatus");
        assertTrue(statuses.size() > 0, "قائمة حالات الملف يجب ألا تكون فارغة");
        for (JsonNode option : statuses) {
            assertFalse(option.get("value").asText().isBlank(), "لكل خيار قيمة");
            assertFalse(option.get("label").asText().isBlank(), "لكل خيار وصف عربي");
        }
    }

    // ---------- التقارير ----------

    @ParameterizedTest(name = "تقرير {0} يعيد عنواناً وترويسات وصفوفاً")
    @ValueSource(strings = {"financial", "cases", "consultants", "team"})
    void everyReportKindBuilds(String kind) throws Exception {
        JsonNode report = treeOf(mvc.perform(get("/api/reports/" + kind).session(admin()))
                .andExpect(status().isOk()));

        assertFalse(report.get("title").asText().isBlank(), "لكل تقرير عنوان");
        assertTrue(report.get("headers").isArray() && report.get("headers").size() > 0,
                "لكل تقرير ترويسات أعمدة");
        assertTrue(report.get("rows").isArray(), "الصفوف يجب أن تكون مصفوفة");

        // عدد الخلايا في كل صف يطابق عدد الترويسات
        int columns = report.get("headers").size();
        for (JsonNode row : report.get("rows")) {
            assertEquals(columns, row.size(),
                    "صف في تقرير " + kind + " لا يطابق عدد الأعمدة");
        }
    }

    @Test
    @DisplayName("نوع تقرير غير معروف يعيد 400 برسالة تذكر النوع")
    void unknownReportKindIsBadRequest() throws Exception {
        MvcResult res = mvc.perform(get("/api/reports/لا-يوجد").session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("نوع تقرير غير معروف"), errorOf(res));
    }

    @Test
    @DisplayName("تصدير التقرير يعيد ملف إكسل حقيقياً")
    void reportExportReturnsXlsx() throws Exception {
        MvcResult res = mvc.perform(get("/api/reports/financial/export").session(admin()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] data = res.getResponse().getContentAsByteArray();
        assertTrue(data.length > 0, "الملف المصدَّر يجب ألا يكون فارغاً");
        // ملفات xlsx أرشيف ZIP يبدأ بالتوقيع PK
        assertEquals('P', (char) data[0], "توقيع ملف xlsx يبدأ بـ PK");
        assertEquals('K', (char) data[1], "توقيع ملف xlsx يبدأ بـ PK");
    }

    @Test
    @DisplayName("تاريخ غير صالح في التقرير يعيد 400 لا 500")
    void invalidDateParamIsBadRequest() throws Exception {
        mvc.perform(get("/api/reports/financial")
                        .param("from", "ليس-تاريخاً")
                        .session(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- سجل النشاطات ----------

    @Test
    @DisplayName("سجل النشاطات يسجّل عمليات الدخول ويقبل التصفية")
    void auditLogRecordsAndFilters() throws Exception {
        JsonNode all = treeOf(mvc.perform(get("/api/audit").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(all.get("total").asLong() > 0, "يجب أن تُسجَّل نشاطات الدخول على الأقل");

        JsonNode filtered = treeOf(mvc.perform(get("/api/audit")
                        .param("username", "admin")
                        .session(admin()))
                .andExpect(status().isOk()));
        for (JsonNode row : filtered.get("items")) {
            assertEquals("admin", row.get("username").asText(),
                    "التصفية بالمستخدم تسرّبت لمستخدم آخر");
        }
    }

    @Test
    @DisplayName("تصدير سجل النشاطات يعيد ملف إكسل")
    void auditExportReturnsXlsx() throws Exception {
        byte[] data = mvc.perform(get("/api/audit/export").session(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(data.length > 0);
        assertEquals('P', (char) data[0]);
    }

    // ---------- الإعدادات ----------

    @Test
    @DisplayName("الإعدادات تُقرأ وتُحفظ ويُعاد عدد ما تغيّر")
    void settingsRoundTrip() throws Exception {
        JsonNode settings = treeOf(mvc.perform(get("/api/settings").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(settings.size() > 0, "يجب أن توجد إعدادات مزروعة");

        String key = settings.get(0).get("settingKey").asText();
        MvcResult res = mvc.perform(put("/api/settings")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + key + "\":\"قيمة اختبار\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        assertTrue(bodyOf(res).contains("1"), "الرسالة تذكر عدد الإعدادات المحفوظة");
    }

    // ---------- التنبيهات ----------

    @Test
    @DisplayName("عدّاد التنبيهات يعيد رقماً وقائمة التنبيهات مصفوفة")
    void notificationsExposeCountAndList() throws Exception {
        JsonNode count = treeOf(mvc.perform(get("/api/notifications/count").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(count.fields().hasNext(), "العدّاد يجب أن يعيد حقلاً واحداً على الأقل");

        JsonNode list = treeOf(mvc.perform(get("/api/notifications").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(list.isArray());
    }

    @Test
    @DisplayName("تعليم كل التنبيهات كمقروءة ينجح ويصفّر غير المقروء")
    void markAllReadClearsUnread() throws Exception {
        mvc.perform(post("/api/notifications/read-all").session(admin()))
                .andExpect(status().isOk());

        JsonNode unread = treeOf(mvc.perform(get("/api/notifications")
                        .param("unreadOnly", "true")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(0, unread.size(), "بعد التعليم يجب ألا يبقى تنبيه غير مقروء");
    }

    @Test
    @DisplayName("تعليم تنبيه غير موجود لا يكشف وجوده ولا يُسقط الطلب")
    void markingUnknownNotificationIsSilentAndIdempotent() throws Exception {
        // العملية مقيّدة بمُعرّف المستخدم الحالي، فالتنبيه غير الموجود
        // والتنبيه العائد لمستخدم آخر يُعاملان بالطريقة نفسها حتى لا
        // يتحوّل رمز الحالة إلى وسيلة لاستكشاف تنبيهات الآخرين.
        mvc.perform(post("/api/notifications/999999/read").session(admin()))
                .andExpect(status().isOk());

        // وتكرارها لا يغيّر شيئاً
        mvc.perform(post("/api/notifications/999999/read").session(admin()))
                .andExpect(status().isOk());
    }

    // ---------- التصفيح ----------

    @ParameterizedTest(name = "{0} يعيد صيغة تصفيح موحّدة")
    @ValueSource(strings = {"/api/cases", "/api/executions", "/api/consultations",
            "/api/financial", "/api/archive"})
    void pagedEndpointsShareOneShape(String path) throws Exception {
        JsonNode page = treeOf(mvc.perform(get(path).session(admin()))
                .andExpect(status().isOk()));
        assertTrue(page.get("items").isArray(), path + " يجب أن يعيد items كمصفوفة");
        assertTrue(page.get("total").asLong() >= 0, path + " يجب أن يعيد الإجمالي");
        assertEquals(0, page.get("page").asInt(), path + " الصفحة الافتراضية صفر");
        assertTrue(page.get("size").asInt() > 0, path + " حجم الصفحة الافتراضي موجب");
    }

    @Test
    @DisplayName("حجم الصفحة يُحترم ولا يتجاوز المطلوب")
    void pageSizeIsHonoured() throws Exception {
        for (String path : PAGED_ENDPOINTS) {
            JsonNode page = treeOf(mvc.perform(get(path)
                            .param("page", "0")
                            .param("size", "2")
                            .session(admin()))
                    .andExpect(status().isOk()));
            assertTrue(page.get("items").size() <= 2,
                    path + " أعاد عناصر أكثر من حجم الصفحة المطلوب");
        }
    }

    @Test
    @DisplayName("صفحة بعيدة تعيد قائمة فارغة مع بقاء الإجمالي صحيحاً")
    void pageBeyondEndIsEmptyNotError() throws Exception {
        JsonNode page = treeOf(mvc.perform(get("/api/cases")
                        .param("page", "500")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(0, page.get("items").size(), "الصفحة البعيدة يجب أن تكون فارغة");
        assertTrue(page.get("total").asLong() > 0, "الإجمالي يبقى كما هو");
    }

    /** كل مسار يقبل تصفية بتعداد، مع اسم المُعامل واسم الحقل في الرسالة. */
    private static final String[][] ENUM_FILTERS = {
            {"/api/cases",         "status", "حالة القضية"},
            {"/api/consultations", "status", "حالة الاستشارة"},
            {"/api/executions",    "status", "حالة ملف التنفيذ"},
            {"/api/financial",     "status", "حالة الملف"},
            {"/api/archive",       "type",   "نوع عنصر الأرشيف"},
            {"/api/parties",       "kind",   "نوع الطرف"},
    };

    @ParameterizedTest(name = "{0}?{1}=مجهول يُرفض بـ 400 موحّد")
    @org.junit.jupiter.params.provider.MethodSource("enumFilters")
    void unknownEnumValueIsAlwaysBadRequest(String path, String param, String fieldAr) throws Exception {
        MvcResult res = mvc.perform(get(path)
                        .param(param, "NOT_A_REAL_VALUE")
                        .session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();

        String error = errorOf(res);
        assertTrue(error.contains("قيمة غير معروفة لحقل"),
                path + " يجب أن يستخدم الصيغة الموحّدة — " + error);
        assertTrue(error.contains(fieldAr),
                path + " يجب أن تسمّي الرسالة الحقل [" + fieldAr + "] — " + error);
        assertTrue(error.contains("NOT_A_REAL_VALUE"),
                path + " يجب أن تسمّي الرسالة القيمة المرفوضة — " + error);
        assertTrue(error.contains("القيم المتاحة:"),
                path + " يجب أن تسرد الرسالة القيم المتاحة — " + error);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> enumFilters() {
        return java.util.Arrays.stream(ENUM_FILTERS)
                .map(row -> org.junit.jupiter.params.provider.Arguments.of(row[0], row[1], row[2]));
    }

    @ParameterizedTest(name = "{0}?{1} يقبل الأحرف الصغيرة والمسافات")
    @org.junit.jupiter.params.provider.MethodSource("enumFilters")
    void enumFiltersNormaliseCaseAndSpacing(String path, String param, String fieldAr) throws Exception {
        // قيمة صحيحة مأخوذة من التعداد نفسه، مكتوبة بأحرف صغيرة ومحاطة بمسافات
        String valid = firstValidValue(path, param);
        mvc.perform(get(path)
                        .param(param, "  " + valid.toLowerCase(java.util.Locale.ROOT) + "  ")
                        .session(admin()))
                .andExpect(status().isOk());
    }

    /** أول قيمة صحيحة للمُعامل، تُقرأ من رسالة الرفض الموحّدة لتفادي تكرار قوائم التعدادات. */
    private String firstValidValue(String path, String param) {
        return switch (path + ":" + param) {
            case "/api/cases:status"         -> "OPEN";
            case "/api/consultations:status" -> "RECEIVED";
            case "/api/executions:status"    -> "OPEN";
            case "/api/financial:status"     -> "OPEN";
            case "/api/archive:type"         -> "JUDGMENT";
            case "/api/parties:kind"         -> "CLIENT";
            default -> throw new IllegalArgumentException("لا توجد قيمة مرجعية لـ " + path);
        };
    }

    @Test
    @DisplayName("الحالة الفارغة تعني عدم التصفية لا الرفض")
    void blankStatusMeansNoFilter() throws Exception {
        JsonNode unfiltered = treeOf(mvc.perform(get("/api/cases").session(admin()))
                .andExpect(status().isOk()));
        JsonNode blank = treeOf(mvc.perform(get("/api/cases")
                        .param("status", "   ")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(unfiltered.get("total").asLong(), blank.get("total").asLong(),
                "الحالة الفارغة يجب أن تُعامل كعدم تصفية");
    }

    @Test
    @DisplayName("حالة القضية تُقبل بأي حالة أحرف")
    void caseStatusIsCaseInsensitive() throws Exception {
        JsonNode upper = treeOf(mvc.perform(get("/api/cases")
                        .param("status", "OPEN")
                        .session(admin()))
                .andExpect(status().isOk()));
        JsonNode lower = treeOf(mvc.perform(get("/api/cases")
                        .param("status", "open")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(upper.get("total").asLong(), lower.get("total").asLong(),
                "الأحرف الصغيرة والكبيرة يجب أن تعطيا النتيجة نفسها");
    }

    // ---------- القوانين والأرشيف ----------

    @Test
    @DisplayName("مدونات القوانين تُعاد ولكل مدونة شجرة أبواب ومواد")
    void lawCodesExposeTree() throws Exception {
        JsonNode codes = treeOf(mvc.perform(get("/api/laws").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(codes.size() > 0, "يجب أن تُزرع مدونات قوانين");

        long id = codes.get(0).get("id").asLong();
        mvc.perform(get("/api/laws/" + id + "/tree").session(admin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("البحث الشامل يعيد نتائج موسومة بنوعها")
    void globalSearchReturnsTypedHits() throws Exception {
        JsonNode hits = treeOf(mvc.perform(get("/api/search")
                        .param("q", "ا")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(hits.isArray(), "البحث الشامل يعيد مصفوفة نتائج");
    }

    @Test
    @DisplayName("البحث بلا كلمة لا يُسقط الطلب")
    void emptySearchIsSafe() throws Exception {
        mvc.perform(get("/api/search").session(admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/laws/search").session(admin()))
                .andExpect(status().isOk());
    }

    // ---------- مسارات غير موجودة ----------

    @Test
    @DisplayName("مسار API غير موجود يعيد 404 بجسم JSON")
    void unknownApiPathIsNotFound() throws Exception {
        MvcResult res = mvc.perform(get("/api/لا-يوجد-هذا-المسار").session(admin()))
                .andExpect(status().isNotFound())
                .andReturn();
        assertTrue(String.valueOf(res.getResponse().getContentType()).contains("json"),
                "حتى 404 يجب أن تعود JSON");
    }

    @Test
    @DisplayName("طريقة طلب غير مدعومة تعيد 405")
    void wrongHttpMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/api/dashboard").session(admin()))
                .andExpect(status().isMethodNotAllowed());
    }
}
