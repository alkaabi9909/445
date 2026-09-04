package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.qanoon.domain.Party;
import com.qanoon.repo.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * اختبارات متحكم الأطراف (الموكلون والمدينون): التحقق من المدخلات،
 * التصفية والبحث، والتعطيل بدل الحذف حفاظاً على سلامة السجلات.
 */
class PartyControllerTest extends AbstractControllerTest {

    @Autowired PartyRepository partyRepository;

    // ---------- القراءة ----------

    @Test
    @DisplayName("قائمة الأطراف مرتّبة أبجدياً بالاسم")
    void partiesAreSortedByName() throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/parties").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(arr.isArray() && arr.size() > 0, "يجب أن تُعاد الأطراف المزروعة");

        String previous = null;
        for (JsonNode p : arr) {
            String name = p.get("name").asText();
            if (previous != null) {
                assertTrue(previous.compareTo(name) <= 0,
                        "الترتيب الأبجدي مكسور عند: " + previous + " ثم " + name);
            }
            previous = name;
        }
    }

    @Test
    @DisplayName("التصفية بالنوع تعيد الموكلين فقط")
    void filterByKindReturnsClientsOnly() throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/parties")
                        .param("kind", "CLIENT")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(arr.size() > 0, "يجب أن يوجد موكلون في البيانات التجريبية");
        for (JsonNode p : arr) {
            assertEquals("CLIENT", p.get("kind").asText(), "التصفية تسرّبت لنوع آخر");
        }
    }

    @Test
    @DisplayName("نوع طرف غير معروف يُرفض بـ 400 بدل أن يُتجاهل صامتاً")
    void unknownKindIsRejected() throws Exception {
        // التجاهل الصامت كان يجعل التصفية تبدو وكأنها طُبِّقت بينما تُعاد كل
        // الأطراف، فيرى المستخدم مدينين في شاشة الموكلين دون أي تحذير.
        MvcResult res = mvc.perform(get("/api/parties")
                        .param("kind", "NOT_A_KIND")
                        .session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("نوع الطرف"), errorOf(res));
        assertTrue(errorOf(res).contains("NOT_A_KIND"), errorOf(res));
    }

    @Test
    @DisplayName("النوع الفارغ يعني عدم التصفية لا الرفض")
    void blankKindMeansNoFilter() throws Exception {
        JsonNode all = treeOf(mvc.perform(get("/api/parties").session(admin())).andReturn());
        JsonNode blank = treeOf(mvc.perform(get("/api/parties")
                        .param("kind", "   ")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(all.size(), blank.size(),
                "الفراغ يُعامل كعدم تصفية");
    }

    @Test
    @DisplayName("النوع بأحرف صغيرة يُقبل ويُصفّي فعلاً")
    void lowercaseKindFiltersCorrectly() throws Exception {
        JsonNode upper = treeOf(mvc.perform(get("/api/parties")
                        .param("kind", "CLIENT").session(admin())).andReturn());
        JsonNode lower = treeOf(mvc.perform(get("/api/parties")
                        .param("kind", "client").session(admin()))
                .andExpect(status().isOk()));
        assertEquals(upper.size(), lower.size(),
                "حالة الأحرف يجب ألا تغيّر النتيجة");
        assertTrue(lower.size() > 0 && lower.size() < treeOf(
                        mvc.perform(get("/api/parties").session(admin())).andReturn()).size(),
                "التصفية بأحرف صغيرة يجب أن تُطبَّق فعلاً لا أن تُتجاهل");
    }

    @Test
    @DisplayName("البحث بالاسم يطابق جزئياً")
    void searchMatchesPartialName() throws Exception {
        JsonNode all = treeOf(mvc.perform(get("/api/parties").session(admin())).andReturn());
        String firstName = all.get(0).get("name").asText();
        String fragment = firstName.length() > 3 ? firstName.substring(0, 3) : firstName;

        JsonNode hits = treeOf(mvc.perform(get("/api/parties")
                        .param("q", fragment)
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(hits.size() > 0, "البحث بجزء من الاسم يجب أن يعيد نتائج");
    }

    @Test
    @DisplayName("بحث بلا نتائج يعيد قائمة فارغة لا خطأ")
    void searchWithNoHitsReturnsEmptyList() throws Exception {
        JsonNode hits = treeOf(mvc.perform(get("/api/parties")
                        .param("q", "طرف-غير-موجود-إطلاقاً-٩٩٩")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertEquals(0, hits.size());
    }

    // ---------- الكتابة ----------

    @Test
    @DisplayName("الاسم والنوع مطلوبان عند الإضافة")
    void createValidatesRequiredFields() throws Exception {
        MvcResult noName = mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CLIENT\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noName).contains("اسم"), errorOf(noName));

        MvcResult noKind = mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"بلا نوع\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noKind).contains("نوع"), errorOf(noKind));
    }

    @Test
    @DisplayName("الاسم المكوّن من مسافات فقط يُرفض")
    void blankNameIsRejected() throws Exception {
        mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"kind\":\"CLIENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("الاسم يُشذّب من المسافات الزائدة عند الحفظ")
    void nameIsTrimmedOnSave() throws Exception {
        String name = "موكل مشذّب " + System.nanoTime();
        JsonNode created = treeOf(mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  " + name + "  \",\"kind\":\"CLIENT\"}"))
                .andExpect(status().isOk()));
        assertEquals(name, created.get("data").get("name").asText(),
                "المسافات المحيطة يجب أن تُزال");
    }

    @Test
    @DisplayName("النوع الافتراضي للطرف هو شخص طبيعي")
    void partyTypeDefaultsToIndividual() throws Exception {
        JsonNode created = treeOf(mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"افتراضي " + System.nanoTime() + "\",\"kind\":\"DEBTOR\"}"))
                .andExpect(status().isOk()));
        assertEquals("INDIVIDUAL", created.get("data").get("partyType").asText());
    }

    @Test
    @DisplayName("دورة حياة كاملة: إضافة ثم تعديل ثم تعطيل")
    void partyFullLifecycle() throws Exception {
        String name = "طرف اختبار " + System.nanoTime();

        JsonNode created = treeOf(mvc.perform(post("/api/parties")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"kind\":\"CLIENT\","
                                + "\"phone\":\"050-9999999\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true)));
        long id = created.get("data").get("id").asLong();

        mvc.perform(put("/api/parties/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + " معدّل\",\"kind\":\"CLIENT\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name + " معدّل"));

        mvc.perform(delete("/api/parties/" + id).session(admin()))
                .andExpect(status().isOk());

        Party after = partyRepository.findById(id).orElseThrow();
        assertFalse(after.isActive(), "الحذف يجب أن يُعطّل الطرف لا أن يمحوه");
        assertTrue(partyRepository.existsById(id), "السجل يجب أن يبقى في قاعدة البيانات");
    }

    @Test
    @DisplayName("تعطيل طرف مرتبط بملفات يشرح أنه لم يُحذف")
    void deactivatingLinkedPartyExplainsItself() throws Exception {
        JsonNode parties = treeOf(mvc.perform(get("/api/parties")
                        .param("kind", "CLIENT")
                        .session(admin()))
                .andReturn());

        // أول موكل مزروع مرتبط بملفات مالية أو قضايا
        long id = parties.get(0).get("id").asLong();
        MvcResult res = mvc.perform(delete("/api/parties/" + id).session(admin()))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(bodyOf(res).contains("تعطيل"),
                "الرسالة يجب أن توضّح التعطيل — " + bodyOf(res));
    }

    @Test
    @DisplayName("طرف غير موجود يعيد 404 على التعديل والتعطيل")
    void unknownPartyIsNotFound() throws Exception {
        mvc.perform(put("/api/parties/999999")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"شبح\",\"kind\":\"CLIENT\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/parties/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("معرّف غير رقمي يعيد 400 لا 500")
    void nonNumericIdIsBadRequest() throws Exception {
        mvc.perform(delete("/api/parties/abc").session(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- الصلاحيات ----------

    @Test
    @DisplayName("من يملك العرض فقط لا يستطيع الإضافة")
    void viewOnlyRoleCannotCreate() throws Exception {
        // المستشار يملك CLIENTS_VIEW دون CLIENTS_MANAGE
        mvc.perform(get("/api/parties").session(consultant()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/parties")
                        .session(consultant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"محاولة\",\"kind\":\"CLIENT\"}"))
                .andExpect(status().isForbidden());
    }
}
