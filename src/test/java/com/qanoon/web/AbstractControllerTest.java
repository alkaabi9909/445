package com.qanoon.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * أساس اختبارات المتحكمات: سياق واحد مشترك بقاعدة H2 مستقلة عن
 * {@code QanoonEndToEndTest} حتى لا تتداخل البيانات بين الملفين.
 *
 * <p>الجلسات مخزّنة في ذاكرة مؤقتة لكل مستخدم لأن النظام يسمح بجلستين
 * متزامنتين فقط لكل حساب ({@code SecurityConfig.MAX_SESSIONS_PER_USER})؛
 * فتح جلسة جديدة في كل اختبار كان سيُنهي الجلسات السابقة صامتاً.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:qanoon-web-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "qanoon.storage-path=./target/test-uploads",
        "qanoon.backup-path=./target/test-backups"
})
abstract class AbstractControllerTest {

    /** كلمة المرور الافتراضية لكل الحسابات التجريبية. */
    protected static final String PASSWORD = "Qanoon@123";

    private static final Map<String, MockHttpSession> SESSIONS = new ConcurrentHashMap<>();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    // ---------- الجلسات ----------

    /** جلسة مسجّلة الدخول للمستخدم، تُعاد نفسها عند الطلب مرة أخرى. */
    protected MockHttpSession sessionFor(String username) {
        return SESSIONS.computeIfAbsent(username, u -> {
            try {
                MockHttpSession s = new MockHttpSession();
                int status = mvc.perform(post("/api/auth/login")
                                .session(s)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(credentials(u, PASSWORD)))
                        .andReturn().getResponse().getStatus();
                if (status != 200) {
                    throw new IllegalStateException(
                            "تعذّر تسجيل الدخول للحساب التجريبي " + u + " — رمز الحالة " + status);
                }
                return s;
            } catch (Exception ex) {
                throw new IllegalStateException("خطأ أثناء تسجيل دخول " + u, ex);
            }
        });
    }

    protected MockHttpSession admin()      { return sessionFor("admin"); }
    protected MockHttpSession lawyer()     { return sessionFor("lawyer1"); }
    protected MockHttpSession consultant() { return sessionFor("consult1"); }
    protected MockHttpSession senior()     { return sessionFor("senior1"); }
    protected MockHttpSession secretary()  { return sessionFor("secretary1"); }

    /** جلسة جديدة غير مخزّنة — للحالات التي تختبر الدخول نفسه. */
    protected MockHttpSession freshLogin(String username, String password) throws Exception {
        MockHttpSession s = new MockHttpSession();
        int status = mvc.perform(post("/api/auth/login")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andReturn().getResponse().getStatus();
        return status == 200 ? s : null;
    }

    protected static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // ---------- قراءة الردود ----------

    protected String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected JsonNode treeOf(MvcResult result) throws Exception {
        return json.readTree(bodyOf(result));
    }

    protected JsonNode treeOf(ResultActions actions) throws Exception {
        return treeOf(actions.andReturn());
    }

    /** نص حقل {@code error} من رد الخطأ الموحّد. */
    protected String errorOf(MvcResult result) throws Exception {
        JsonNode node = treeOf(result).get("error");
        return node == null ? "" : node.asText();
    }
}
