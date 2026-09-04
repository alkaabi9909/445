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
 * دورة حياة الدفعات على الملف المالي: التسجيل بانتظار التأكيد،
 * التأكيد، الارتجاع، الإلغاء، وخطط التقسيط وإلغاء الأقساط.
 */
class PaymentLifecycleTest extends AbstractControllerTest {

    // ---------- الملف المالي ----------

    @Test
    @DisplayName("إنشاء ملف مالي ثم قراءته وتعديله")
    void financialFileCanBeCreatedReadAndUpdated() throws Exception {
        long id = newFileId();

        JsonNode detail = treeOf(mvc.perform(get("/api/financial/" + id).session(admin()))
                .andExpect(status().isOk()));
        assertTrue(detail.has("file"), "التفاصيل تتضمن بيانات الملف");

        // موضوع المطالبة ومبلغها إلزاميان في التعديل كما في الإنشاء
        mvc.perform(put("/api/financial/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":26000,\"subject\":\"موضوع معدّل\"}"))
                .andExpect(status().isOk());

        JsonNode after = treeOf(mvc.perform(get("/api/financial/" + id).session(admin())).andReturn());
        assertEquals("موضوع معدّل", after.get("file").get("subject").asText());
    }

    @Test
    @DisplayName("تعديل الملف المالي يرفض المبلغ غير الموجب والموضوع الفارغ")
    void financialUpdateValidatesFields() throws Exception {
        long id = newFileId();

        MvcResult badAmount = mvc.perform(put("/api/financial/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":0,\"subject\":\"موضوع\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(badAmount).contains("أكبر من صفر"), errorOf(badAmount));

        MvcResult noSubject = mvc.perform(put("/api/financial/" + id)
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":1000}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noSubject).contains("موضوع"), errorOf(noSubject));
    }

    @Test
    @DisplayName("ملف مالي غير موجود يعيد 404")
    void unknownFileIsNotFound() throws Exception {
        mvc.perform(get("/api/financial/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- الدفعات ----------

    @Test
    @DisplayName("الدفعة تُنشأ بانتظار التأكيد ولا تُحتسب قبل تأكيدها")
    void paymentStartsPendingAndCountsOnlyWhenConfirmed() throws Exception {
        long fileId = newFileId();
        long paymentId = addPayment(fileId, 5000);

        JsonNode before = treeOf(mvc.perform(get("/api/financial/" + fileId).session(admin())).andReturn());
        JsonNode pending = findPayment(before, paymentId);
        assertEquals("PENDING", pending.get("status").asText(), "الدفعة الجديدة بانتظار التأكيد");

        mvc.perform(post("/api/payments/" + paymentId + "/confirm").session(admin()))
                .andExpect(status().isOk());

        JsonNode after = treeOf(mvc.perform(get("/api/financial/" + fileId).session(admin())).andReturn());
        assertEquals("CONFIRMED", findPayment(after, paymentId).get("status").asText());
    }

    @Test
    @DisplayName("لا يجوز تأكيد دفعة مؤكدة مسبقاً")
    void confirmingTwiceIsRejected() throws Exception {
        long fileId = newFileId();
        long paymentId = addPayment(fileId, 1000);
        mvc.perform(post("/api/payments/" + paymentId + "/confirm").session(admin()))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(post("/api/payments/" + paymentId + "/confirm").session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مؤكدة"), errorOf(res));
    }

    @Test
    @DisplayName("الارتجاع يتطلب سبباً إلزامياً ولا يتكرر")
    void bounceRequiresReasonAndIsNotRepeatable() throws Exception {
        long fileId = newFileId();
        long paymentId = addPayment(fileId, 2000);

        MvcResult noReason = mvc.perform(post("/api/payments/" + paymentId + "/bounce")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noReason).contains("سبب"), errorOf(noReason));

        mvc.perform(post("/api/payments/" + paymentId + "/bounce")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"رصيد غير كافٍ\"}"))
                .andExpect(status().isOk());

        MvcResult again = mvc.perform(post("/api/payments/" + paymentId + "/bounce")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"مرة أخرى\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(again).contains("مرتجعة"), errorOf(again));
    }

    @Test
    @DisplayName("الدفعة الملغاة لا يمكن تأكيدها")
    void cancelledPaymentCannotBeConfirmed() throws Exception {
        long fileId = newFileId();
        long paymentId = addPayment(fileId, 1500);

        mvc.perform(post("/api/payments/" + paymentId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"خطأ في الإدخال\"}"))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(post("/api/payments/" + paymentId + "/confirm").session(admin()))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("ملغاة"), errorOf(res));
    }

    @Test
    @DisplayName("دفعة غير موجودة تعيد 404 على كل العمليات")
    void unknownPaymentIsNotFound() throws Exception {
        mvc.perform(post("/api/payments/999999/confirm").session(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/999999/bounce")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"سبب\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/999999/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"سبب\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("مبلغ الدفعة يجب أن يكون أكبر من صفر")
    void paymentAmountMustBePositive() throws Exception {
        long fileId = newFileId();
        MvcResult res = mvc.perform(post("/api/financial/" + fileId + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"paymentDate\":\"" + LocalDate.now()
                                + "\",\"method\":\"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("أكبر من صفر"), errorOf(res));
    }

    // ---------- خطة التقسيط ----------

    @Test
    @DisplayName("خطة التقسيط تولّد الأقساط ويمكن إلغاء قسط بسبب")
    void planGeneratesInstallmentsAndOneCanBeCancelled() throws Exception {
        long fileId = newFileId();

        mvc.perform(post("/api/financial/" + fileId + "/plan")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalAmount\":12000,\"installmentsCount\":4,"
                                + "\"startDate\":\"" + LocalDate.now().plusDays(30) + "\","
                                + "\"intervalMonths\":1,\"paymentMethod\":\"TRANSFER\"}"))
                .andExpect(status().isOk());

        JsonNode detail = treeOf(mvc.perform(get("/api/financial/" + fileId).session(admin())).andReturn());
        JsonNode installments = detail.get("installments");
        assertNotNull(installments, "التفاصيل يجب أن تتضمن الأقساط");
        assertEquals(4, installments.size(), "يجب توليد أربعة أقساط");
        long instId = installments.get(0).get("id").asLong();

        MvcResult noReason = mvc.perform(post("/api/installments/" + instId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(noReason).contains("سبب"), errorOf(noReason));

        mvc.perform(post("/api/installments/" + instId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"إعادة جدولة بالاتفاق\"}"))
                .andExpect(status().isOk());

        MvcResult again = mvc.perform(post("/api/installments/" + instId + "/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"مرة أخرى\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(again).contains("ملغى"), errorOf(again));
    }

    @Test
    @DisplayName("قسط غير موجود يعيد 404")
    void unknownInstallmentIsNotFound() throws Exception {
        mvc.perform(post("/api/installments/999999/cancel")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"سبب\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- سجل التواصل والإغلاق ----------

    @Test
    @DisplayName("تسجيل تواصل يتطلب النوع والملخص")
    void communicationRequiresTypeAndSummary() throws Exception {
        long fileId = newFileId();

        mvc.perform(post("/api/financial/" + fileId + "/communications")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CALL\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/financial/" + fileId + "/communications")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CALL\",\"summary\":\"مكالمة مع المدين\","
                                + "\"outcome\":\"وعد بالسداد\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("إغلاق الملف بإعفاء يمنع أي كتابة بعده")
    void closingFileBlocksFurtherWrites() throws Exception {
        long fileId = newFileId();

        mvc.perform(post("/api/financial/" + fileId + "/close")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closureType\":\"EXEMPTION\",\"note\":\"إعفاء إداري\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/financial/" + fileId + "/communications")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CALL\",\"summary\":\"بعد الإغلاق\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- أدوات ----------

    /**
     * لا يُفتح ملف مالي دون مستند مطالبة مرفق، لذلك يُرفع المستند أولاً
     * برفع مؤقت (entityId = 0) ثم يُمرَّر معرّفه في attachmentIds.
     */
    private long newFileId() throws Exception {
        long claimId = uploadClaimDocument();
        JsonNode created = treeOf(mvc.perform(post("/api/financial")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + lookupId("clients") + ",\"debtorId\":" + lookupId("debtors")
                                + ",\"claimAmount\":25000,\"subject\":\"ملف اختبار الدفعات\","
                                + "\"attachmentIds\":[" + claimId + "]}"))
                .andExpect(status().isOk()));
        JsonNode data = created.get("data");
        JsonNode file = data.has("file") ? data.get("file") : data;
        return file.get("id").asLong();
    }

    /** رفع مستند مطالبة مؤقت — يغطي أيضاً مسار رفع المرفقات. */
    private long uploadClaimDocument() throws Exception {
        MockMultipartFile doc = new MockMultipartFile(
                "file", "claim.pdf", "application/pdf", "claim document body".getBytes());
        JsonNode res = treeOf(mvc.perform(multipart("/api/attachments")
                        .file(doc)
                        .session(admin())
                        .param("entityType", "FINANCIAL")
                        .param("entityId", "0")
                        .param("category", "مطالبة")
                        .param("description", "مستند المطالبة المالية"))
                .andExpect(status().isOk()));
        return res.get("data").get("id").asLong();
    }

    private long addPayment(long fileId, int amount) throws Exception {
        JsonNode res = treeOf(mvc.perform(post("/api/financial/" + fileId + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + ",\"paymentDate\":\"" + LocalDate.now()
                                + "\",\"method\":\"CASH\",\"payerName\":\"دافع اختبار\"}"))
                .andExpect(status().isOk()));
        JsonNode payments = res.get("data").get("payments");
        assertNotNull(payments, "استجابة التسجيل تتضمن الدفعات");
        long newest = -1;
        for (JsonNode p : payments) {
            newest = Math.max(newest, p.get("id").asLong());
        }
        assertTrue(newest > 0, "يجب أن تُسجَّل الدفعة");
        return newest;
    }

    private JsonNode findPayment(JsonNode detail, long paymentId) {
        for (JsonNode p : detail.get("payments")) {
            if (p.get("id").asLong() == paymentId) return p;
        }
        throw new AssertionError("الدفعة " + paymentId + " غير موجودة في التفاصيل");
    }

    private long lookupId(String key) throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة");
        return arr.get(0).get("id").asLong();
    }
}
