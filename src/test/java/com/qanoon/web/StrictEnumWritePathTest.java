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
 * المسارات المالية التي تُغيّر الحالة تُطابق رموز التعداد حرفياً:
 * لا يُقبل {@code "cash"} بدل {@code "CASH"}، بخلاف مُرشّحات القراءة
 * التي تتسامح مع حالة الأحرف.
 *
 * <p>هذا فرق مقصود لا سهو: تسجيل دفعة وإنهاء ملف إجراءان نهائيان
 * يجب أن يرسل فيهما العميل القيمة المعتمدة بلا تخمين من الخادم.</p>
 */
class StrictEnumWritePathTest extends AbstractControllerTest {

    // ---------- طريقة السداد ----------

    @Test
    @DisplayName("تسجيل دفعة بحروف صغيرة يُرفض بـ 400")
    void lowercasePaymentMethodIsRejected() throws Exception {
        MvcResult res = postPayment(openFileId(), "cash");
        assertEquals(400, res.getResponse().getStatus(),
                "المطابقة الحرفية يجب أن ترفض الحروف الصغيرة");
        assertTrue(errorOf(res).contains("طريقة السداد"), errorOf(res));
        assertTrue(errorOf(res).contains("القيم المتاحة:"),
                "الرسالة تسرد القيم المتاحة — " + errorOf(res));
    }

    @Test
    @DisplayName("تسجيل دفعة بحروف مختلطة يُرفض أيضاً")
    void mixedCasePaymentMethodIsRejected() throws Exception {
        assertEquals(400, postPayment(openFileId(), "Cash").getResponse().getStatus());
        assertEquals(400, postPayment(openFileId(), "cAsH").getResponse().getStatus());
    }

    @Test
    @DisplayName("الرمز المعتمد بحروف كبيرة يُقبل")
    void canonicalPaymentMethodIsAccepted() throws Exception {
        MvcResult res = postPayment(openFileId(), "CASH");
        assertEquals(200, res.getResponse().getStatus(),
                "الرمز المعتمد يجب أن يُقبل — " + errorOf(res));
    }

    @Test
    @DisplayName("المسافات المحيطة تُشذّب حتى في المطابقة الحرفية")
    void surroundingSpacesAreStillTrimmed() throws Exception {
        assertEquals(200, postPayment(openFileId(), "  CASH  ").getResponse().getStatus());
    }

    @Test
    @DisplayName("طريقة سداد مفقودة تُرفض برسالتها الخاصة")
    void missingPaymentMethodHasItsOwnMessage() throws Exception {
        MvcResult res = postPayment(openFileId(), null);
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("طريقة السداد مطلوبة"),
                "رسالة الغياب تبقى مصاغة بالعربية الصحيحة — " + errorOf(res));
    }

    // ---------- وجهة إنهاء الملف ----------

    @Test
    @DisplayName("إنهاء الملف بحروف صغيرة يُرفض بـ 400")
    void lowercaseClosureTypeIsRejected() throws Exception {
        MvcResult res = postClose(openFileId(), "exemption");
        assertEquals(400, res.getResponse().getStatus(),
                "إنهاء الملف إجراء نهائي فلا يُخمَّن الرمز");
        assertTrue(errorOf(res).contains("وجهة إنهاء الملف"), errorOf(res));
    }

    @Test
    @DisplayName("وجهة إنهاء غير معروفة تُرفض وتسرد الوجهات المتاحة")
    void unknownClosureTypeListsOptions() throws Exception {
        MvcResult res = postClose(openFileId(), "NOT_A_CLOSURE");
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("القيم المتاحة:"), errorOf(res));
        assertTrue(errorOf(res).contains("إعفاء"),
                "القيم المتاحة تُعرض بأوصافها العربية — " + errorOf(res));
    }

    @Test
    @DisplayName("وجهة إنهاء مفقودة تُرفض برسالتها الخاصة")
    void missingClosureTypeHasItsOwnMessage() throws Exception {
        MvcResult res = postClose(openFileId(), null);
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("وجهة إنهاء الملف مطلوبة"), errorOf(res));
    }

    // ---------- نوع التواصل ----------

    @Test
    @DisplayName("نوع تواصل بحروف صغيرة يُرفض")
    void lowercaseCommTypeIsRejected() throws Exception {
        MvcResult res = postCommunication(openFileId(), "call");
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("نوع التواصل"), errorOf(res));
    }

    @Test
    @DisplayName("نوع تواصل مفقود يُرفض برسالته الخاصة")
    void missingCommTypeHasItsOwnMessage() throws Exception {
        MvcResult res = postCommunication(openFileId(), null);
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("نوع التواصل إلزامي"), errorOf(res));
    }

    // ---------- خطة التقسيط: حقل اختياري لكن حرفي ----------

    @Test
    @DisplayName("طريقة سداد خطة التقسيط اختيارية لكنها حرفية عند إرسالها")
    void planPaymentMethodIsOptionalButExact() throws Exception {
        MvcResult res = postPlan(openFileId(), "cheque");
        assertEquals(400, res.getResponse().getStatus(),
                "الحقل اختياري، لكن القيمة المُرسلة تُطابق حرفياً");
        assertTrue(errorOf(res).contains("طريقة السداد"), errorOf(res));
    }

    // ---------- أولوية الاستشارة: اختيارية وحرفية ----------

    @Test
    @DisplayName("أولوية استشارة بحروف صغيرة تُرفض بدل أن تُقبل ضمناً")
    void lowercasePriorityIsRejected() throws Exception {
        MvcResult res = postConsultation("urgent");
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("أولوية الاستشارة"), errorOf(res));
    }

    @Test
    @DisplayName("أولوية مفقودة تعني «عادية» ولا تُرفض")
    void missingPriorityDefaultsToNormal() throws Exception {
        MvcResult res = postConsultation(null);
        assertEquals(200, res.getResponse().getStatus(),
                "الأولوية اختيارية — " + errorOf(res));
    }

    // ---------- التباين المقصود مع مرشّحات القراءة ----------

    @Test
    @DisplayName("مرشّح القراءة يبقى متسامحاً بينما مسار الكتابة حرفي")
    void readFilterStaysLenientWhileWriteStaysStrict() throws Exception {
        // نفس النظام، سلوكان مختلفان عن قصد
        mvc.perform(get("/api/financial").param("status", "open").session(admin()))
                .andExpect(status().isOk());

        assertEquals(400, postPayment(openFileId(), "cash").getResponse().getStatus());
    }

    @Test
    @DisplayName("كل مسارات الكتابة على نفس التعداد تتفق: لا تسامح في أي منها")
    void everyPaymentMethodWritePathAgrees() throws Exception {
        // نفس الحقل (طريقة السداد) على ثلاثة مسارات كتابة مختلفة
        assertEquals(400, postPayment(openFileId(), "cash").getResponse().getStatus(),
                "دفعة على ملف مالي");
        assertEquals(400, postPlan(openFileId(), "cash").getResponse().getStatus(),
                "خطة تقسيط");
        assertEquals(400, postExecutionPayment("cash").getResponse().getStatus(),
                "دفعة على ملف تنفيذ");
    }

    @Test
    @DisplayName("نوع أمر التنفيذ حرفي أيضاً")
    void executionOrderTypeIsExact() throws Exception {
        MvcResult res = postExecutionOrder("travel_ban");
        assertEquals(400, res.getResponse().getStatus());
        assertTrue(errorOf(res).contains("نوع أمر التنفيذ"), errorOf(res));
    }

    // ---------- أدوات ----------

    /** ملف مالي غير مغلق، وإلا رُفض الطلب لسبب آخر قبل الوصول لتحليل التعداد. */
    private long openFileId() throws Exception {
        JsonNode items = treeOf(mvc.perform(get("/api/financial").session(admin()))
                .andExpect(status().isOk())).get("items");
        for (JsonNode f : items) {
            String status = f.get("status").asText();
            if (!status.startsWith("CLOSED") && !"ARCHIVED".equals(status)
                    && !"EXEMPTED".equals(status) && !"ESCALATED".equals(status)) {
                return f.get("id").asLong();
            }
        }
        throw new AssertionError("لا يوجد ملف مالي مفتوح في البيانات التجريبية");
    }

    private MvcResult postPayment(long fileId, String method) throws Exception {
        String methodJson = method == null ? "null" : "\"" + method + "\"";
        return mvc.perform(post("/api/financial/" + fileId + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"paymentDate\":\"2026-01-15\","
                                + "\"method\":" + methodJson + ",\"payerName\":\"دافع اختبار\"}"))
                .andReturn();
    }

    private MvcResult postClose(long fileId, String closureType) throws Exception {
        String typeJson = closureType == null ? "null" : "\"" + closureType + "\"";
        return mvc.perform(post("/api/financial/" + fileId + "/close")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closureType\":" + typeJson + ",\"note\":\"إنهاء اختباري\"}"))
                .andReturn();
    }

    private MvcResult postCommunication(long fileId, String type) throws Exception {
        String typeJson = type == null ? "null" : "\"" + type + "\"";
        return mvc.perform(post("/api/financial/" + fileId + "/communications")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":" + typeJson + ",\"summary\":\"ملخص اختباري\"}"))
                .andReturn();
    }

    private MvcResult postPlan(long fileId, String paymentMethod) throws Exception {
        String methodJson = paymentMethod == null ? "null" : "\"" + paymentMethod + "\"";
        return mvc.perform(post("/api/financial/" + fileId + "/plan")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalAmount\":1200,\"installmentsCount\":3,"
                                + "\"startDate\":\"2026-02-01\",\"intervalMonths\":1,"
                                + "\"paymentMethod\":" + methodJson + "}"))
                .andReturn();
    }

    private MvcResult postConsultation(String priority) throws Exception {
        String priorityJson = priority == null ? "null" : "\"" + priority + "\"";
        long clientId = anyClientId();
        return mvc.perform(post("/api/consultations")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId + ",\"subject\":\"موضوع اختباري\","
                                + "\"requestText\":\"نص الطلب الاختباري\","
                                + "\"priority\":" + priorityJson + "}"))
                .andReturn();
    }

    private MvcResult postExecutionPayment(String method) throws Exception {
        String methodJson = method == null ? "null" : "\"" + method + "\"";
        return mvc.perform(post("/api/executions/" + anyExecutionId() + "/payments")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50,\"paymentDate\":\"2026-01-15\","
                                + "\"method\":" + methodJson + "}"))
                .andReturn();
    }

    private MvcResult postExecutionOrder(String orderType) throws Exception {
        String typeJson = orderType == null ? "null" : "\"" + orderType + "\"";
        return mvc.perform(post("/api/executions/" + anyExecutionId() + "/orders")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderType\":" + typeJson + ",\"orderNumber\":\"AMR-1\","
                                + "\"issuedDate\":\"2026-01-15\"}"))
                .andReturn();
    }

    /** ملف تنفيذ غير مغلق، وإلا رُفض الطلب لسبب آخر قبل الوصول لتحليل التعداد. */
    private long anyExecutionId() throws Exception {
        JsonNode items = treeOf(mvc.perform(get("/api/executions").session(admin()))
                .andExpect(status().isOk())).get("items");
        for (JsonNode f : items) {
            String status = f.get("status").asText();
            if ("OPEN".equals(status) || "IN_PROGRESS".equals(status)) {
                return f.get("id").asLong();
            }
        }
        throw new AssertionError("لا يوجد ملف تنفيذ مفتوح في البيانات التجريبية");
    }

    private long anyClientId() throws Exception {
        JsonNode clients = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get("clients");
        assertTrue(clients.size() > 0, "يجب أن يوجد موكلون");
        return clients.get(0).get("id").asLong();
    }
}
