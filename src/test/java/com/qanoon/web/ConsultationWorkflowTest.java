package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * دورة حياة الاستشارة كاملة: التسجيل، الإسناد، كتابة الرأي، الرفع للمراجعة،
 * المراجعة، التوقيع، الإرسال، الأرشفة، والتصحيح برأي جديد.
 *
 * <p>القاعدتان الملزمتان المختبَرتان هنا: لا يراجع كاتبُ الرأي رأيَه،
 * وما وُقِّع لا يُكتب فوقه ولو من المدير.</p>
 */
class ConsultationWorkflowTest extends AbstractControllerTest {

    // ---------- التسجيل ----------

    @Test
    @DisplayName("تسجيل الاستشارة يتطلب الموكل والموضوع ونص الطلب")
    void createValidatesRequiredFields() throws Exception {
        String[][] cases = {
                {"{}", "الموكل"},
                {"{\"clientId\":" + clientId() + "}", "موضوع"},
                {"{\"clientId\":" + clientId() + ",\"subject\":\"موضوع\"}", "نص الطلب"},
        };
        for (String[] c : cases) {
            MvcResult res = mvc.perform(post("/api/consultations")
                            .session(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c[0]))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertTrue(errorOf(res).contains(c[1]),
                    "الرسالة يجب أن تذكر [" + c[1] + "] — " + errorOf(res));
        }
    }

    @Test
    @DisplayName("الطرف المحدد يجب أن يكون موكلاً لا مديناً")
    void createRejectsDebtorAsClient() throws Exception {
        MvcResult res = mvc.perform(post("/api/consultations")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + debtorId() + ",\"subject\":\"موضوع\","
                                + "\"requestText\":\"نص\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("موكل"), errorOf(res));
    }

    @Test
    @DisplayName("الاستشارة الجديدة تبدأ بحالة الاستلام ورقم متسلسل")
    void newConsultationStartsReceived() throws Exception {
        JsonNode data = createConsultation("استشارة جديدة");
        assertEquals("RECEIVED", data.get("consultation").get("status").asText());
        assertFalse(data.get("consultation").get("consultationNumber").asText().isBlank());
    }

    // ---------- الإسناد ----------

    @Test
    @DisplayName("الإسناد اليدوي يثبّت المستشار ويغيّر الحالة إلى دراسة")
    void manualAssignmentSetsConsultant() throws Exception {
        long id = newConsultationId("استشارة للإسناد");
        mvc.perform(post("/api/consultations/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultantId\":" + consultantId() + "}"))
                .andExpect(status().isOk());

        JsonNode detail = detail(id);
        assertFalse(detail.get("consultation").get("consultant").isNull(),
                "يجب أن يُثبَّت المستشار");
        assertEquals("STUDYING", detail.get("consultation").get("status").asText());
    }

    @Test
    @DisplayName("الإسناد التلقائي بلا مستشار محدد يختار مرشحاً")
    void automaticAssignmentPicksCandidate() throws Exception {
        long id = newConsultationId("استشارة إسناد تلقائي");
        mvc.perform(post("/api/consultations/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        assertFalse(detail(id).get("consultation").get("consultant").isNull(),
                "الإسناد التلقائي يجب أن يختار مستشاراً");
    }

    @Test
    @DisplayName("استشارة غير موجودة تعيد 404")
    void unknownConsultationIsNotFound() throws Exception {
        mvc.perform(get("/api/consultations/999999").session(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- الرأي والمراجعة ----------

    @Test
    @DisplayName("كاتب الرأي لا يجوز أن يراجع رأيه")
    void authorCannotReviewOwnOpinion() throws Exception {
        long id = newConsultationId("استشارة المراجعة المزدوجة");
        assignTo(id, "consult1");

        MockHttpSession author = sessionFor("consult1");
        mvc.perform(post("/api/consultations/" + id + "/opinion")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinionText\":\"الرأي القانوني المفصّل في هذه المسألة.\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/consultations/" + id + "/submit-review").session(author))
                .andExpect(status().isOk());
        assertEquals("UNDER_REVIEW", detail(id).get("consultation").get("status").asText());

        MvcResult res = mvc.perform(post("/api/consultations/" + id + "/review")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"اعتماد ذاتي\"}"))
                .andReturn();
        assertTrue(res.getResponse().getStatus() >= 400,
                "كاتب الرأي يجب أن يُمنع من مراجعة رأيه — " + res.getResponse().getStatus());
    }

    @Test
    @DisplayName("الإعادة بملاحظات تُرجع الاستشارة للدراسة وتزيد عدّاد الإعادة")
    void rejectingReviewReturnsToStudying() throws Exception {
        long id = newConsultationId("استشارة معادة");
        assignTo(id, "consult1");
        writeOpinionAndSubmit(id, "consult1");

        mvc.perform(post("/api/consultations/" + id + "/review")
                        .session(senior())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"notes\":\"يرجى تعزيز الرأي بالسند القانوني\"}"))
                .andExpect(status().isOk());

        JsonNode c = detail(id).get("consultation");
        assertEquals("RETURNED", c.get("status").asText());
        assertTrue(c.get("returnedCount").asInt() >= 1, "يجب أن يزيد عدّاد الإعادة");
    }

    // ---------- التوقيع والقفل ----------

    @Test
    @DisplayName("الرأي الموقّع مقفل حتى على المدير، والتصحيح برأي جديد")
    void signedOpinionIsLockedAndCorrectedByNewOpinion() throws Exception {
        long id = newConsultationId("استشارة للتوقيع");
        assignTo(id, "consult1");
        writeOpinionAndSubmit(id, "consult1");

        mvc.perform(post("/api/consultations/" + id + "/review")
                        .session(senior())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"معتمد\"}"))
                .andExpect(status().isOk());
        assertEquals("APPROVED", detail(id).get("consultation").get("status").asText());

        mvc.perform(post("/api/consultations/" + id + "/sign").session(senior()))
                .andExpect(status().isOk());
        JsonNode signed = detail(id).get("consultation");
        assertEquals("SIGNED", signed.get("status").asText());
        assertTrue(signed.get("locked").asBoolean(), "التوقيع يجب أن يقفل الرأي");

        MvcResult res = mvc.perform(post("/api/consultations/" + id + "/opinion")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinionText\":\"محاولة تعديل بعد التوقيع\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(errorOf(res).contains("مقفل") || errorOf(res).contains("موقّع"), errorOf(res));

        MvcResult correction = mvc.perform(post("/api/consultations/" + id + "/correction")
                        .session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationNumber").exists())
                .andReturn();
        assertFalse(bodyOf(correction).isBlank());
    }

    @Test
    @DisplayName("لا يجوز التوقيع قبل الاعتماد")
    void cannotSignBeforeApproval() throws Exception {
        long id = newConsultationId("استشارة توقيع سابق لأوانه");
        assignTo(id, "consult1");
        mvc.perform(post("/api/consultations/" + id + "/sign").session(senior()))
                .andExpect(status().isBadRequest());
    }

    // ---------- الإرسال والأرشفة ----------

    @Test
    @DisplayName("الإرسال بعد التوقيع ثم الأرشفة ينقلان الحالة بالترتيب")
    void sendThenArchiveAdvancesStatus() throws Exception {
        long id = newConsultationId("استشارة للإرسال");
        assignTo(id, "consult1");
        writeOpinionAndSubmit(id, "consult1");
        mvc.perform(post("/api/consultations/" + id + "/review")
                        .session(senior())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"notes\":\"معتمد\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/consultations/" + id + "/sign").session(senior()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/consultations/" + id + "/send")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentTo\":\"client@example.com\"}"))
                .andExpect(status().isOk());
        assertEquals("SENT", detail(id).get("consultation").get("status").asText());

        mvc.perform(post("/api/consultations/" + id + "/archive").session(admin()))
                .andExpect(status().isOk());
        assertEquals("ARCHIVED", detail(id).get("consultation").get("status").asText());
    }

    @Test
    @DisplayName("لا يجوز الإرسال قبل التوقيع")
    void cannotSendBeforeSigning() throws Exception {
        long id = newConsultationId("استشارة إرسال سابق لأوانه");
        mvc.perform(post("/api/consultations/" + id + "/send")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentTo\":\"client@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- أدوات ----------

    private JsonNode createConsultation(String subject) throws Exception {
        return treeOf(mvc.perform(post("/api/consultations")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId() + ",\"subject\":\"" + subject + "\","
                                + "\"requestText\":\"نص طلب الاستشارة للاختبار\","
                                + "\"specialization\":\"تجاري\",\"priority\":\"NORMAL\"}"))
                .andExpect(status().isOk()))
                .get("data");
    }

    private long newConsultationId(String subject) throws Exception {
        return createConsultation(subject).get("consultation").get("id").asLong();
    }

    private JsonNode detail(long id) throws Exception {
        return treeOf(mvc.perform(get("/api/consultations/" + id).session(admin()))
                .andExpect(status().isOk()));
    }

    private void assignTo(long id, String username) throws Exception {
        mvc.perform(post("/api/consultations/" + id + "/assign")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultantId\":" + userIdOf(username) + "}"))
                .andExpect(status().isOk());
    }

    private void writeOpinionAndSubmit(long id, String username) throws Exception {
        MockHttpSession s = sessionFor(username);
        mvc.perform(post("/api/consultations/" + id + "/opinion")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinionText\":\"الرأي القانوني المفصّل في هذه المسألة.\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/consultations/" + id + "/submit-review").session(s))
                .andExpect(status().isOk());
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

    private long clientId() throws Exception {
        return lookupId("clients");
    }

    private long debtorId() throws Exception {
        return lookupId("debtors");
    }

    private long consultantId() throws Exception {
        return lookupId("consultants");
    }

    private long lookupId(String key) throws Exception {
        JsonNode arr = treeOf(mvc.perform(get("/api/lookups").session(admin()))
                .andExpect(status().isOk())).get(key);
        assertTrue(arr != null && arr.size() > 0, "قائمة " + key + " فارغة");
        return arr.get(0).get("id").asLong();
    }
}
