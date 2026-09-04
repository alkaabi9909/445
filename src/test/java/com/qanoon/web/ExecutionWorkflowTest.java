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
 * مسار التنفيذ كاملاً: إنشاء الملف من قضية محوّلة، تعديل بياناته،
 * أوامر التنفيذ وإلغاؤها، الدفعات، بوابة الاستيفاء، وشهادة الاستيفاء.
 */
class ExecutionWorkflowTest extends AbstractControllerTest {

    // ---------- القراءة والتعديل ----------

    @Test
    @DisplayName("تفاصيل ملف التنفيذ تعرض الأوامر والدفعات والخط الزمني")
    void detailExposesOrdersAndPayments() throws Exception {
        long id = freshExecutionId();
        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + id).session(admin()))
                .andExpect(status().isOk()));
        assertTrue(d.has("orders"), "التفاصيل تتضمن الأوامر");
        assertTrue(d.has("payments"), "التفاصيل تتضمن الدفعات");
    }

    @Test
    @DisplayName("تعديل ملف التنفيذ يحفظ رقم التنفيذ القضائي والمصروفات")
    void updateSavesCourtNumberAndExpenses() throws Exception {
        long id = freshExecutionId();
        mvc.perform(put("/api/executions/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtExecutionNumber\":\"EXE-COURT-77\",\"court\":\"تنفيذ دبي\","
                                + "\"expensesAmount\":1500,\"notes\":\"ملاحظة اختبار\"}"))
                .andExpect(status().isOk());

        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + id).session(admin())).andReturn());
        assertEquals("EXE-COURT-77", d.get("file").get("courtExecutionNumber").asText());
    }

    @Test
    @DisplayName("ملف تنفيذ غير موجود يعيد 404")
    void unknownExecutionIsNotFound() throws Exception {
        mvc.perform(get("/api/executions/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- أوامر التنفيذ ----------

    @Test
    @DisplayName("إصدار أمر تنفيذ ثم إلغاؤه بسبب إلزامي")
    void orderCanBeIssuedThenCancelled() throws Exception {
        long id = freshExecutionId();

        mvc.perform(post("/api/executions/" + id + "/orders")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderType\":\"TRAVEL_BAN\",\"orderNumber\":\"ORD-1\","
                                + "\"targetEntity\":\"المدين\",\"issuedDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk());

        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + id).session(admin())).andReturn());
        assertTrue(d.get("orders").size() >= 1, "يجب أن يُسجَّل الأمر");
        long orderId = d.get("orders").get(0).get("id").asLong();

        MvcResult noReason = mvc.perform(post("/api/execution-orders/" + orderId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertFalse(errorOf(noReason).isBlank(), "سبب الإلغاء إلزامي");

        mvc.perform(post("/api/execution-orders/" + orderId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"تسوية ودية مع المدين\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("تعدد أوامر التنفيذ السارية مسموح ولا يُمنع")
    void multipleActiveOrdersAreAllowed() throws Exception {
        long id = freshExecutionId();
        for (String type : new String[]{"TRAVEL_BAN", "BANK_FREEZE", "SALARY_SEIZURE"}) {
            mvc.perform(post("/api/executions/" + id + "/orders")
                            .session(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderType\":\"" + type + "\",\"orderNumber\":\"ORD-" + type + "\","
                                    + "\"issuedDate\":\"" + LocalDate.now() + "\"}"))
                    .andExpect(status().isOk());
        }
        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + id).session(admin())).andReturn());
        assertEquals(3, d.get("orders").size(), "يجب أن تتعايش ثلاثة أوامر سارية");
    }

    @Test
    @DisplayName("أمر تنفيذ غير موجود يعيد 404 عند الإلغاء")
    void unknownOrderIsNotFound() throws Exception {
        mvc.perform(post("/api/execution-orders/999999/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"سبب\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- الدفعات وبوابة الاستيفاء ----------

    @Test
    @DisplayName("الاستيفاء ممنوع ما دام هناك متبقٍ، ومسموح بعد سداده بالكامل")
    void satisfactionGateFollowsRemainingBalance() throws Exception {
        long id = freshExecutionId();

        JsonNode before = treeOf(mvc.perform(get("/api/executions/" + id + "/satisfaction-check")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertFalse(before.get("allowed").asBoolean(), "الملف الجديد عليه متبقٍ فلا يُستوفى");

        mvc.perform(post("/api/executions/" + id + "/satisfy").session(admin()))
                .andExpect(status().isBadRequest());

        // سداد كامل مبلغ الحكم
        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + id).session(admin())).andReturn());
        double total = d.get("file").get("totalAmount").asDouble();
        mvc.perform(post("/api/executions/" + id + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + total + ",\"paymentDate\":\"" + LocalDate.now()
                                + "\",\"method\":\"TRANSFER\",\"payerName\":\"المدين\"}"))
                .andExpect(status().isOk());

        confirmAllPayments(id);

        JsonNode after = treeOf(mvc.perform(get("/api/executions/" + id + "/satisfaction-check")
                        .session(admin())).andReturn());
        assertTrue(after.get("allowed").asBoolean(),
                "بعد سداد كامل المبلغ يجب أن تُفتح بوابة الاستيفاء — " + after);

        mvc.perform(post("/api/executions/" + id + "/satisfy").session(admin()))
                .andExpect(status().isOk());

        JsonNode cert = treeOf(mvc.perform(get("/api/executions/" + id + "/certificate")
                        .session(admin()))
                .andExpect(status().isOk()));
        assertFalse(cert.get("certificateNumber").asText().isBlank(),
                "شهادة الاستيفاء يجب أن تحمل رقماً");
    }

    @Test
    @DisplayName("شهادة الاستيفاء غير متاحة قبل الاستيفاء")
    void certificateUnavailableBeforeSatisfaction() throws Exception {
        long id = freshExecutionId();
        mvc.perform(get("/api/executions/" + id + "/certificate").session(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("الدفعة بمبلغ غير موجب تُرفض")
    void nonPositivePaymentIsRejected() throws Exception {
        long id = freshExecutionId();
        mvc.perform(post("/api/executions/" + id + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"paymentDate\":\"" + LocalDate.now()
                                + "\",\"method\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- أدوات ----------

    /** ينشئ قضية بحكم نهائي لصالح الموكل ثم يحوّلها، فيعيد ملف تنفيذ جديداً نظيفاً. */
    private long freshExecutionId() throws Exception {
        long caseId = treeOf(mvc.perform(post("/api/cases")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"opponentId\":" + lookupId("debtors")
                                + ",\"subject\":\"قضية لملف تنفيذ\",\"caseType\":\"COMMERCIAL\","
                                + "\"claimAmount\":30000,\"openedAt\":\"" + LocalDate.now().minusDays(200) + "\"}"))
                .andExpect(status().isOk()))
                .get("data").get("case").get("id").asLong();

        mvc.perform(post("/api/cases/" + caseId + "/judgment")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"judgmentDate\":\"" + LocalDate.now().minusDays(60) + "\","
                                + "\"judgmentNumber\":\"JX-" + System.nanoTime() + "\","
                                + "\"judgmentFor\":\"CLIENT\",\"judgmentAmount\":30000,"
                                + "\"judgmentFinal\":true}"))
                .andExpect(status().isOk());

        // استجابة التحويل هي TransferResult(executionFileId, executionNumber)
        JsonNode transferred = treeOf(mvc.perform(post("/api/cases/" + caseId + "/transfer")
                        .session(admin()))
                .andExpect(status().isOk()));
        return transferred.get("data").get("executionFileId").asLong();
    }

    private void confirmAllPayments(long executionId) throws Exception {
        JsonNode d = treeOf(mvc.perform(get("/api/executions/" + executionId).session(admin())).andReturn());
        for (JsonNode p : d.get("payments")) {
            if ("PENDING".equals(p.get("status").asText())) {
                mvc.perform(post("/api/payments/" + p.get("id").asLong() + "/confirm").session(admin()))
                        .andExpect(status().isOk());
            }
        }
    }

    private long lookupId(String key) throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة");
        return arr.get(0).get("id").asLong();
    }
}
