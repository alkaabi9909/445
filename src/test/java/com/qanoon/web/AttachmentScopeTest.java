package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ضبط وصول المرفقات على الملف المالي وملف التنفيذ والقضية —
 * آخر ما تبقّى بلا تنفيذ فعلي بحسب قياس التغطية.
 */
class AttachmentScopeTest extends AbstractControllerTest {


    // ---------- وصول المرفقات على الملف المالي ----------

    /**
     * نوع الكيان المعتمد للملف المالي هو {@code FINANCIAL_FILE} كما في
     * {@code FinancialService.ENTITY_TYPE} وكما ترسله الواجهة؛ أي قيمة أخرى
     * تسقط في الفرع الافتراضي فتُقاس صلاحية المدير بدل ضبط الوصول الدقيق.
     */
    private static final String FINANCIAL_ENTITY = "FINANCIAL_FILE";

    @Test
    @DisplayName("مرفقات الملف المالي متاحة لمن يملك الاطلاع ومحجوبة عن غيره")
    void financialAttachmentsAreScoped() throws Exception {
        long fileId = newFinancialFileId();

        mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(fileId))
                        .session(admin()))
                .andExpect(status().isOk());

        // السكرتير يملك FINANCIAL_VIEW_ALL فيرى كل الملفات
        mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(fileId))
                        .session(secretary()))
                .andExpect(status().isOk());

        // المستشار لا يملك FINANCIAL_VIEW إطلاقاً
        MvcResult denied = mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(fileId))
                        .session(consultant()))
                .andReturn();
        assertEquals(403, denied.getResponse().getStatus(),
                "المستشار يجب أن يُمنع من مرفقات الملفات المالية");
    }

    @Test
    @DisplayName("المحامي يرى مرفقات ملفه المالي المسند إليه فقط دون ملفات غيره")
    void lawyerSeesOnlyOwnFinancialAttachments() throws Exception {
        long mine = newFinancialFileAssignedTo("lawyer1");
        long other = newFinancialFileAssignedTo("lawyer2");

        mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(mine))
                        .session(sessionFor("lawyer1")))
                .andExpect(status().isOk());

        MvcResult denied = mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(other))
                        .session(sessionFor("lawyer1")))
                .andReturn();
        assertEquals(403, denied.getResponse().getStatus(),
                "المحامي لا يملك FINANCIAL_VIEW_ALL فلا يرى ملف زميله");
    }

    @Test
    @DisplayName("رفع مرفق على الملف المالي ثم إدراجه")
    void canUploadAttachmentOnFinancialFile() throws Exception {
        long fileId = newFinancialFileId();

        mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "receipt.txt", "text/plain", "receipt".getBytes()))
                        .session(admin())
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(fileId))
                        .param("category", "إيصال"))
                .andExpect(status().isOk());

        JsonNode list = treeOf(mvc.perform(get("/api/attachments")
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", String.valueOf(fileId))
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(list.size() >= 1, "يجب أن يظهر المرفق على الملف المالي");
    }

    // ---------- وصول المرفقات على ملف التنفيذ ----------

    @Test
    @DisplayName("مرفقات ملف التنفيذ متاحة للمدير ومحجوبة عن المستشار")
    void executionAttachmentsAreScoped() throws Exception {
        long execId = newExecutionId();

        mvc.perform(get("/api/attachments")
                        .param("entityType", "EXECUTION")
                        .param("entityId", String.valueOf(execId))
                        .session(admin()))
                .andExpect(status().isOk());

        MvcResult denied = mvc.perform(get("/api/attachments")
                        .param("entityType", "EXECUTION")
                        .param("entityId", String.valueOf(execId))
                        .session(consultant()))
                .andReturn();
        assertEquals(403, denied.getResponse().getStatus(),
                "المستشار يجب أن يُمنع من مرفقات ملفات التنفيذ");
    }

    @Test
    @DisplayName("مرفقات القضية محجوبة عن من لا يملك صلاحية القضايا")
    void caseAttachmentsAreScoped() throws Exception {
        long caseId = newCaseId();
        MvcResult denied = mvc.perform(get("/api/attachments")
                        .param("entityType", "CASE")
                        .param("entityId", String.valueOf(caseId))
                        .session(consultant()))
                .andReturn();
        assertEquals(403, denied.getResponse().getStatus(),
                "المستشار لا يملك CASE_VIEW فيجب أن يُمنع");
    }

    // ---------- أدوات ----------

    private long newFinancialFileAssignedTo(String username) throws Exception {
        long claimId = uploadClaim();
        JsonNode created = treeOf(mvc.perform(post("/api/financial")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":9000,\"subject\":\"ملف مسند إلى " + username + "\","
                                + "\"assignedLawyerId\":" + userIdOf(username) + ","
                                + "\"attachmentIds\":[" + claimId + "]}"))
                .andExpect(status().isOk()));
        JsonNode data = created.get("data");
        return (data.has("file") ? data.get("file") : data).get("id").asLong();
    }

    private long userIdOf(String username) throws Exception {
        JsonNode users = treeOf(mvc.perform(get("/api/users").session(admin()))
                .andExpect(status().isOk()));
        for (JsonNode u : users) {
            if (username.equals(u.get("username").asText())) return u.get("id").asLong();
        }
        throw new AssertionError("المستخدم " + username + " غير موجود");
    }

    private long uploadClaim() throws Exception {
        return treeOf(mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "claim.pdf", "application/pdf", "claim".getBytes()))
                        .session(admin())
                        .param("entityType", FINANCIAL_ENTITY)
                        .param("entityId", "0")
                        .param("category", "مطالبة"))
                .andExpect(status().isOk()))
                .get("data").get("id").asLong();
    }

    private long newFinancialFileId() throws Exception {
        long claimId = treeOf(mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "claim.pdf", "application/pdf", "claim".getBytes()))
                        .session(admin())
                        .param("entityType", "FINANCIAL")
                        .param("entityId", "0")
                        .param("category", "مطالبة"))
                .andExpect(status().isOk()))
                .get("data").get("id").asLong();

        JsonNode created = treeOf(mvc.perform(post("/api/financial")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":9000,\"subject\":\"ملف لاختبار المرفقات\","
                                + "\"attachmentIds\":[" + claimId + "]}"))
                .andExpect(status().isOk()));
        JsonNode data = created.get("data");
        return (data.has("file") ? data.get("file") : data).get("id").asLong();
    }

    private long newCaseId() throws Exception {
        return treeOf(mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"opponentId\":" + lookupId("debtors")
                                + ",\"subject\":\"قضية لنطاق المرفقات\",\"openedAt\":\""
                                + LocalDate.now().minusDays(200) + "\"}"))
                .andExpect(status().isOk()))
                .get("data").get("case").get("id").asLong();
    }

    private long newExecutionId() throws Exception {
        long caseId = newCaseId();
        mvc.perform(post("/api/cases/" + caseId + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"JS-" + System.nanoTime() + "\","
                                + "\"judgmentFor\":\"CLIENT\",\"judgmentAmount\":9000,"
                                + "\"judgmentFinal\":true}"))
                .andExpect(status().isOk());
        return treeOf(mvc.perform(post("/api/cases/" + caseId + "/transfer").session(admin()))
                .andExpect(status().isOk()))
                .get("data").get("executionFileId").asLong();
    }

    private long lookupId(String key) throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة");
        return arr.get(0).get("id").asLong();
    }
}
