package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * دورة حياة القضية كاملة عبر الواجهة: الإنشاء، التعديل، الإسناد، الجلسات،
 * توثيق الحكم، الاستئناف والفصل فيه، بوابة التحويل، ثم الإغلاق.
 */
class CaseWorkflowTest extends AbstractControllerTest {

    // ---------- الإنشاء والتعديل ----------

    @Test
    @DisplayName("إنشاء القضية يتطلب الموكل والخصم والموضوع")
    void createCaseValidatesRequiredFields() throws Exception {
        MvcResult noClient = mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noClient).contains("الموكل"), errorOf(noClient));

        MvcResult noSubject = mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId() + ",\"opponentId\":" + debtorId() + "}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noSubject).contains("موضوع"), errorOf(noSubject));
    }

    @Test
    @DisplayName("الموكل يجب أن يكون من نوع موكل والخصم من نوع مدين")
    void createCaseRejectsWrongPartyKind() throws Exception {
        // تمرير مدين في خانة الموكل
        MvcResult res = mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + debtorId() + ",\"opponentId\":" + debtorId()
                                + ",\"subject\":\"نوع طرف خاطئ\"}"))
                .andReturn();
        assertTrue(res.getResponse().getStatus() >= 400,
                "لا يجوز أن يكون الخصم موكلاً في الوقت نفسه");
    }

    @Test
    @DisplayName("تعديل القضية يحفظ الحقول القابلة للتعديل")
    void updateCaseSavesEditableFields() throws Exception {
        long id = newCaseId("قضية للتعديل");
        mvc.perform(put("/api/cases/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId() + ",\"opponentId\":" + debtorId()
                                + ",\"subject\":\"موضوع معدّل\",\"court\":\"محكمة أبوظبي\"}"))
                .andExpect(status().isOk());

        JsonNode detail = treeOf(mvc.perform(get("/api/cases/" + id).session(admin()))
                .andExpect(status().isOk()));
        assertEquals("موضوع معدّل", detail.get("case").get("subject").asText());
    }

    @Test
    @DisplayName("قضية غير موجودة تعيد 404")
    void unknownCaseIsNotFound() throws Exception {
        mvc.perform(get("/api/cases/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- الإسناد ----------

    @Test
    @DisplayName("إسناد القضية يتطلب محامياً وسبباً مكتوباً")
    void assignRequiresLawyerAndReason() throws Exception {
        long id = newCaseId("قضية للإسناد");

        MvcResult noLawyer = mvc.perform(post("/api/cases/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"بلا محامٍ\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noLawyer).contains("المحامي"), errorOf(noLawyer));

        MvcResult noReason = mvc.perform(post("/api/cases/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lawyerId\":" + lawyerId() + "}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noReason).contains("سبب"),
                "سبب الإسناد إلزامي — " + errorOf(noReason));
    }

    @Test
    @DisplayName("الإسناد الصحيح ينجح ويثبت المحامي على القضية")
    void assignSucceedsWithReason() throws Exception {
        long id = newCaseId("قضية مسندة");
        mvc.perform(post("/api/cases/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lawyerId\":" + lawyerId() + ",\"reason\":\"خبرة في القضايا التجارية\"}"))
                .andExpect(status().isOk());

        JsonNode detail = treeOf(mvc.perform(get("/api/cases/" + id).session(admin())).andReturn());
        assertFalse(detail.get("case").get("assignedLawyer").isNull(),
                "يجب أن يُثبَّت المحامي المسند");
    }

    // ---------- الجلسات ----------

    @Test
    @DisplayName("إضافة جلسة ثم تعديلها تنعكس في تفاصيل القضية")
    void hearingCanBeAddedAndUpdated() throws Exception {
        long id = newCaseId("قضية بجلسات");

        mvc.perform(post("/api/cases/" + id + "/hearings")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hearingDate\":\"" + LocalDate.now().plusDays(10) + "\","
                                + "\"type\":\"FIRST\",\"court\":\"محكمة دبي الابتدائية\","
                                + "\"room\":\"قاعة 3\"}"))
                .andExpect(status().isOk());

        JsonNode detail = treeOf(mvc.perform(get("/api/cases/" + id).session(admin())).andReturn());
        JsonNode hearings = detail.get("hearings");
        assertTrue(hearings.size() >= 1, "يجب أن تُسجَّل الجلسة");
        long hearingId = hearings.get(0).get("id").asLong();

        mvc.perform(put("/api/hearings/" + hearingId)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hearingDate\":\"" + LocalDate.now().plusDays(10) + "\","
                                + "\"type\":\"PLEADING\",\"result\":\"تأجيل للمرافعة\",\"attended\":true}"))
                .andExpect(status().isOk());

        JsonNode after = treeOf(mvc.perform(get("/api/cases/" + id).session(admin())).andReturn());
        assertEquals("تأجيل للمرافعة", after.get("hearings").get(0).get("result").asText());
    }

    @Test
    @DisplayName("جلسة غير موجودة تعيد 404")
    void unknownHearingIsNotFound() throws Exception {
        mvc.perform(put("/api/hearings/999999")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hearingDate\":\"" + LocalDate.now() + "\",\"type\":\"FIRST\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- الحكم والاستئناف ----------

    @Test
    @DisplayName("توثيق الحكم يفتح أجل الطعن ويظهر في بوابة التحويل")
    void recordingJudgmentFeedsTransferGate() throws Exception {
        long id = newCaseId("قضية بحكم");

        mvc.perform(post("/api/cases/" + id + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"J-2026-001\",\"judgmentFor\":\"CLIENT\","
                                + "\"judgmentAmount\":50000,\"judgmentFinal\":true}"))
                .andExpect(status().isOk());

        JsonNode check = treeOf(mvc.perform(get("/api/cases/" + id + "/transfer-check")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertTrue(check.get("allowed").asBoolean(),
                "حكم نهائي لصالح الموكل بلا استئناف يجب أن يجتاز البوابة — " + check);
    }

    @Test
    @DisplayName("الحكم لصالح الخصم يمنع التحويل")
    void judgmentForOpponentBlocksTransfer() throws Exception {
        long id = newCaseId("قضية خاسرة");
        mvc.perform(post("/api/cases/" + id + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"J-2026-002\",\"judgmentFor\":\"OPPONENT\","
                                + "\"judgmentFinal\":true}"))
                .andExpect(status().isOk());

        JsonNode check = treeOf(mvc.perform(get("/api/cases/" + id + "/transfer-check")
                        .session(admin())).andReturn());
        assertFalse(check.get("allowed").asBoolean(), "لا يُحوَّل حكم لصالح الخصم");
        mvc.perform(post("/api/cases/" + id + "/transfer").session(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("تسجيل استئناف يمنع التحويل حتى يُفصل فيه")
    void pendingAppealBlocksTransferUntilDecided() throws Exception {
        long id = newCaseId("قضية مستأنفة");
        mvc.perform(post("/api/cases/" + id + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"J-2026-003\",\"judgmentFor\":\"CLIENT\","
                                + "\"judgmentFinal\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/cases/" + id + "/appeals")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appealNumber\":\"A-2026-001\",\"filedDate\":\""
                                + LocalDate.now().minusDays(5) + "\",\"filedBy\":\"OPPONENT\","
                                + "\"court\":\"محكمة الاستئناف\"}"))
                .andExpect(status().isOk());

        JsonNode blocked = treeOf(mvc.perform(get("/api/cases/" + id + "/transfer-check")
                        .session(admin())).andReturn());
        assertFalse(blocked.get("allowed").asBoolean(), "الاستئناف المعلّق يمنع التحويل");

        JsonNode detail = treeOf(mvc.perform(get("/api/cases/" + id).session(admin())).andReturn());
        long appealId = detail.get("appeals").get(0).get("id").asLong();

        mvc.perform(post("/api/appeals/" + appealId + "/decide")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WITHDRAWN\",\"decisionDate\":\"" + LocalDate.now()
                                + "\",\"decisionSummary\":\"سحب الخصم استئنافه\"}"))
                .andExpect(status().isOk());

        JsonNode after = treeOf(mvc.perform(get("/api/cases/" + id + "/transfer-check")
                        .session(admin())).andReturn());
        assertTrue(after.get("allowed").asBoolean(),
                "بعد سحب الاستئناف يجب أن تُفتح البوابة — " + after);
    }

    @Test
    @DisplayName("استئناف غير موجود يعيد 404 عند الفصل فيه")
    void unknownAppealIsNotFound() throws Exception {
        mvc.perform(post("/api/appeals/999999/decide")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECIDED\",\"decisionDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- التحويل والإغلاق ----------

    @Test
    @DisplayName("التحويل ينشئ ملف تنفيذ ولا يجوز تكراره")
    void transferCreatesExecutionFileOnce() throws Exception {
        long id = newCaseId("قضية للتحويل");
        mvc.perform(post("/api/cases/" + id + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"J-2026-004\",\"judgmentFor\":\"CLIENT\","
                                + "\"judgmentAmount\":75000,\"judgmentFinal\":true}"))
                .andExpect(status().isOk());

        MvcResult first = mvc.perform(post("/api/cases/" + id + "/transfer").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionNumber").exists())
                .andReturn();
        assertFalse(bodyOf(first).isBlank());

        mvc.perform(post("/api/cases/" + id + "/transfer").session(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("إغلاق القضية يمنع تعديلها بعد ذلك")
    void closingCaseBlocksFurtherEdits() throws Exception {
        long id = newCaseId("قضية للإغلاق");

        mvc.perform(post("/api/cases/" + id + "/close")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"إغلاق إداري\"}"))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(put("/api/cases/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId() + ",\"opponentId\":" + debtorId()
                                + ",\"subject\":\"محاولة تعديل بعد الإغلاق\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مغلقة"), errorOf(res));
    }

    @Test
    @DisplayName("الإغلاق بلا جسم طلب مقبول لأن الملاحظة اختيارية")
    void closeAcceptsMissingBody() throws Exception {
        long id = newCaseId("قضية إغلاق بلا ملاحظة");
        mvc.perform(post("/api/cases/" + id + "/close").session(admin()))
                .andExpect(status().isOk());
    }

    // ---------- أدوات ----------

    /**
     * تُفتح القضية بتاريخ قديم عمداً: النظام يرفض حكماً تاريخه يسبق فتح القضية،
     * فلو فُتحت اليوم لتعذّر توثيق حكم قديم عليها في اختبارات البوابة.
     */
    private long newCaseId(String subject) throws Exception {
        JsonNode created = treeOf(mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId() + ",\"opponentId\":" + debtorId()
                                + ",\"subject\":\"" + subject + "\",\"caseType\":\"COMMERCIAL\","
                                + "\"court\":\"محكمة دبي\",\"claimAmount\":100000,"
                                + "\"openedAt\":\"" + LocalDate.now().minusDays(200) + "\"}"))
                .andExpect(status().isOk()));
        return created.get("data").get("case").get("id").asLong();
    }

    private long clientId() throws Exception {
        return lookupId("clients");
    }

    private long debtorId() throws Exception {
        return lookupId("debtors");
    }

    private long lawyerId() throws Exception {
        return lookupId("lawyers");
    }

    private long lookupId(String key) throws Exception {
        JsonNode lookups = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk()));
        JsonNode arr = lookups.get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة في البيانات التجريبية");
        return arr.get(0).get("id").asLong();
    }
}
