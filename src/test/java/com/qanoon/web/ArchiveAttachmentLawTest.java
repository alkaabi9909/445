package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * الأرشيف والبحوث المحفوظة والمرفقات ومدونات القوانين —
 * المسارات التي لم تكن مغطاة في بقية الاختبارات.
 */
class ArchiveAttachmentLawTest extends AbstractControllerTest {

    // ---------- الأرشيف ----------

    @Test
    @DisplayName("دورة حياة عنصر الأرشيف: إضافة، قراءة، تعديل، حذف")
    void archiveItemFullLifecycle() throws Exception {
        String title = "حكم مرجعي " + System.nanoTime();

        JsonNode created = treeOf(mvc.perform(post("/api/archive")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"JUDGMENT\",\"title\":\"" + title + "\","
                                + "\"category\":\"أحكام تجارية\",\"content\":\"نص الحكم المرجعي\","
                                + "\"keywords\":\"تجاري, مرجع\",\"itemDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk()));
        long id = created.get("data").get("id").asLong();

        mvc.perform(get("/api/archive/" + id).session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title));

        mvc.perform(put("/api/archive/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"JUDGMENT\",\"title\":\"" + title + " معدّل\","
                                + "\"content\":\"نص معدّل\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/archive/" + id).session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title + " معدّل"));

        mvc.perform(delete("/api/archive/" + id).session(admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/archive/" + id).session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("عنصر أرشيف غير موجود يعيد 404")
    void unknownArchiveItemIsNotFound() throws Exception {
        mvc.perform(get("/api/archive/999999").session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/archive/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("تصدير الأرشيف يعيد ملف إكسل")
    void archiveExportReturnsXlsx() throws Exception {
        byte[] data = mvc.perform(get("/api/archive/export").session(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(data.length > 0, "الملف المصدَّر يجب ألا يكون فارغاً");
        assertEquals('P', (char) data[0]);
        assertEquals('K', (char) data[1]);
    }

    @Test
    @DisplayName("تصدير الأرشيف يحترم مُرشّح النوع ويرفض النوع المجهول")
    void archiveExportValidatesTypeFilter() throws Exception {
        mvc.perform(get("/api/archive/export").param("type", "JUDGMENT").session(admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/archive/export").param("type", "NOT_A_TYPE").session(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- البحوث المحفوظة ----------

    @Test
    @DisplayName("حفظ بحث ثم استرجاعه ثم حذفه")
    void savedSearchLifecycle() throws Exception {
        String name = "بحث محفوظ " + System.nanoTime();

        mvc.perform(post("/api/searches")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"query\":\"q=عقد&type=JUDGMENT\"}"))
                .andExpect(status().isOk());

        JsonNode all = treeOf(mvc.perform(get("/api/searches").session(admin()))
                .andExpect(status().isOk()));
        assertTrue(bodyOf(mvc.perform(get("/api/searches").session(admin())).andReturn()).contains(name),
                "البحث المحفوظ يجب أن يظهر في القائمة");

        long id = findSavedSearchId(all, name);
        mvc.perform(delete("/api/searches/" + id).session(admin()))
                .andExpect(status().isOk());

        assertFalse(bodyOf(mvc.perform(get("/api/searches").session(admin())).andReturn()).contains(name),
                "بعد الحذف يجب ألا يظهر البحث");
    }

    // ---------- المرفقات ----------

    @Test
    @DisplayName("رفع مرفق على قضية ثم إدراجه وتنزيله وحذفه")
    void attachmentLifecycleOnCase() throws Exception {
        long caseId = newCaseId();
        byte[] body = "attachment content for case".getBytes();

        JsonNode uploaded = treeOf(mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "memo.txt", "text/plain", body))
                        .session(admin())
                        .param("entityType", "CASE")
                        .param("entityId", String.valueOf(caseId))
                        .param("category", "مذكرة")
                        .param("description", "مذكرة قانونية"))
                .andExpect(status().isOk()));
        long attId = uploaded.get("data").get("id").asLong();

        JsonNode list = treeOf(mvc.perform(get("/api/attachments")
                        .param("entityType", "CASE")
                        .param("entityId", String.valueOf(caseId))
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(list.size() >= 1, "يجب أن يظهر المرفق في قائمة مرفقات القضية");

        byte[] downloaded = mvc.perform(get("/api/attachments/" + attId + "/download").session(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(body, downloaded, "المحتوى المنزَّل يجب أن يطابق المرفوع");

        mvc.perform(delete("/api/attachments/" + attId).session(admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/attachments/" + attId + "/download").session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("مرفق غير موجود يعيد 404")
    void unknownAttachmentIsNotFound() throws Exception {
        mvc.perform(get("/api/attachments/999999/download").session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/attachments/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("إدراج المرفقات يتطلب نوع الكيان ومعرّفه")
    void attachmentListRequiresParams() throws Exception {
        mvc.perform(get("/api/attachments").session(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- مدونات القوانين ----------

    @Test
    @DisplayName("إنشاء مدونة ثم باب ثم مادة، وظهورها في الشجرة والبحث")
    void lawCodeChapterArticleLifecycle() throws Exception {
        String codeName = "قانون اختبار " + System.nanoTime();

        JsonNode code = treeOf(mvc.perform(post("/api/laws")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + codeName + "\",\"lawNumber\":\"TST-" + System.nanoTime()
                                + "\",\"issueYear\":2026,\"jurisdiction\":\"الإمارات\","
                                + "\"description\":\"مدونة للاختبار\"}"))
                .andExpect(status().isOk()));
        long codeId = code.get("data").get("id").asLong();

        JsonNode chapter = treeOf(mvc.perform(post("/api/laws/" + codeId + "/chapters")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"الباب الأول\",\"chapterNumber\":\"1\",\"sortOrder\":1}"))
                .andExpect(status().isOk()));
        assertNotNull(chapter.get("data"), "يجب أن يُنشأ الباب");

        String articleText = "نص المادة الاختبارية الفريدة " + System.nanoTime();
        mvc.perform(post("/api/laws/" + codeId + "/articles")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleNumber\":\"1\",\"title\":\"المادة الأولى\","
                                + "\"articleText\":\"" + articleText + "\"}"))
                .andExpect(status().isOk());

        String tree = bodyOf(mvc.perform(get("/api/laws/" + codeId + "/tree").session(admin()))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(tree.contains("الباب الأول"), "الشجرة يجب أن تعرض الباب");

        JsonNode hits = treeOf(mvc.perform(get("/api/laws/search")
                        .param("q", "الاختبارية")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(hits.isArray(), "البحث في القوانين يعيد مصفوفة");
    }

    @Test
    @DisplayName("مدونة غير موجودة تعيد 404 عند طلب شجرتها")
    void unknownLawCodeIsNotFound() throws Exception {
        mvc.perform(get("/api/laws/999999/tree").session(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("إدارة القوانين تتطلب صلاحية ARCHIVE_MANAGE")
    void lawWritesRequirePermission() throws Exception {
        mvc.perform(post("/api/laws")
                        .session(consultant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"محاولة\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- أدوات ----------

    private long newCaseId() throws Exception {
        JsonNode created = treeOf(mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"opponentId\":" + lookupId("debtors")
                                + ",\"subject\":\"قضية للمرفقات\",\"openedAt\":\""
                                + LocalDate.now().minusDays(30) + "\"}"))
                .andExpect(status().isOk()));
        return created.get("data").get("case").get("id").asLong();
    }

    private long findSavedSearchId(JsonNode searches, String name) {
        for (JsonNode group : searches) {
            if (group.isArray()) {
                for (JsonNode s : group) {
                    if (s.has("name") && name.equals(s.get("name").asText())) {
                        return s.get("id").asLong();
                    }
                }
            } else if (group.has("name") && name.equals(group.get("name").asText())) {
                return group.get("id").asLong();
            }
        }
        throw new AssertionError("البحث المحفوظ [" + name + "] غير موجود في " + searches);
    }

    private long lookupId(String key) throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة");
        return arr.get(0).get("id").asLong();
    }
}
