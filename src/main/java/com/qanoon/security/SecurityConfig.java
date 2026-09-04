package com.qanoon.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import java.io.IOException;
import java.util.List;

/**
 * إعدادات الأمان: مصادقة بالجلسة (HttpSession) بلا صفحة دخول افتراضية.
 * الواجهة تستهلك JSON، لذلك كل رفض يعود كجسم JSON عربي بدل إعادة التوجيه.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** الحد الأقصى للجلسات المتزامنة لكل مستخدم. */
    public static final int MAX_SESSIONS_PER_USER = 2;

    /** المسارات المتاحة بلا مصادقة. */
    private static final String[] PUBLIC_PATHS = {
            "/", "/index.html", "/css/**", "/js/**", "/img/**", "/favicon.ico",
            "/api/auth/login", "/h2-console/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** ينشر أحداث الجلسة حتى يُحذف السجل من SessionRegistry عند انتهائها. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /**
     * استراتيجية الجلسة عند نجاح الدخول اليدوي من {@code /api/auth/login}:
     * فرض الحد الأقصى للجلسات، ثم تغيير معرّف الجلسة، ثم تسجيلها في السجل.
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrent.setMaximumSessions(MAX_SESSIONS_PER_USER);
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    /** 401 بجسم JSON عربي بدل إعادة التوجيه إلى صفحة دخول. */
    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> writeJson(response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "لم تسجّل الدخول أو انتهت جلستك — الرجاء تسجيل الدخول للمتابعة");
    }

    /** 403 بجسم JSON عربي عند نقص الصلاحية. */
    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJson(response,
                HttpServletResponse.SC_FORBIDDEN,
                "ليس لديك صلاحية للقيام بهذا الإجراء");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionRegistry sessionRegistry,
                                                   AuthenticationManager authenticationManager,
                                                   AuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                   AccessDeniedHandler jsonAccessDeniedHandler) throws Exception {
        http
                .authenticationManager(authenticationManager)
                // النظام محلي وواجهته تستهلك JSON — لا حاجة لرمز CSRF على مسارات الـ API
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                // للسماح بإطارات وحدة تحكم H2
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(MAX_SESSIONS_PER_USER)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(expiredSessionStrategy()));
        return http.build();
    }

    private SessionInformationExpiredStrategy expiredSessionStrategy() {
        return event -> writeJson(event.getResponse(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "تم إنهاء هذه الجلسة لفتح جلسات أكثر من المسموح (" + MAX_SESSIONS_PER_USER
                        + ") — الرجاء تسجيل الدخول من جديد");
    }

    private static void writeJson(HttpServletResponse response, int status, String messageAr) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + messageAr + "\",\"blockers\":[]}");
        response.getWriter().flush();
    }
}
