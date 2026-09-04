package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.AuditService;
import com.qanoon.common.BusinessException;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.SettingService;
import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import com.qanoon.security.LoginAttemptService;
import com.qanoon.security.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** الدخول والخروج والملف الشخصي وتغيير كلمة المرور. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String DEFAULT_OFFICE_NAME = "مكتب المحاماة والاستشارات القانونية";
    private static final String[] OFFICE_NAME_KEYS = { "office.name", "officeName", "office_name", "OFFICE_NAME" };

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final LoginAttemptService loginAttemptService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final SettingService settingService;
    private final AuditService auditService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /** تسجيل الدخول: يتحقق من الحساب قبل المصادقة، ثم ينشئ الجلسة يدوياً. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        String username = text(body == null ? null : body.get("username"));
        String password = body == null ? null : body.get("password");
        if (username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("الرجاء إدخال اسم المستخدم وكلمة المرور");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            loginAttemptService.onFailure(username);
            throw new BusinessException("اسم المستخدم أو كلمة المرور غير صحيحة");
        }
        if (!user.isActive()) {
            loginAttemptService.onBlocked(username, "محاولة دخول إلى حساب موقوف");
            throw new BusinessException("الحساب موقوف");
        }
        if (user.isLocked()) {
            String until = lockUntilText(user.getLockedUntil());
            loginAttemptService.onBlocked(username, "محاولة دخول إلى حساب مقفل حتى " + until);
            throw new BusinessException("الحساب مقفل مؤقتاً حتى الساعة " + until);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            loginAttemptService.onFailure(username);
            throw new BusinessException("اسم المستخدم أو كلمة المرور غير صحيحة");
        }

        // إنشاء الجلسة يدوياً وحفظ سياق الأمان فيها
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        loginAttemptService.onSuccess(user);
        User fresh = userRepository.findByUsername(username).orElse(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userDto(fresh));
        result.put("permissions", permissionsOf(fresh));
        return result;
    }

    /** تسجيل الخروج وإنهاء الجلسة. */
    @PostMapping("/logout")
    public ApiResponse logout(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            auditService.logAuth(authentication.getName(), "LOGOUT", true, "تسجيل خروج");
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok("تم تسجيل الخروج بنجاح");
    }

    /** بيانات المستخدم الحالي وصلاحياته وإعدادات المكتب. */
    @GetMapping("/me")
    public Map<String, Object> me() {
        User user = securityUtils.currentUser();

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("currency", settingService.currency());
        settings.put("officeName", officeName());
        settings.put("appealDays", settingService.appealDays());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userDto(user));
        result.put("permissions", permissionsOf(user));
        result.put("settings", settings);
        return result;
    }

    /** تغيير كلمة المرور الشخصية. */
    @PostMapping("/change-password")
    @Transactional
    public ApiResponse changePassword(@RequestBody Map<String, String> body) {
        User user = securityUtils.currentUser();
        String oldPassword = body == null ? null : body.get("oldPassword");
        String newPassword = body == null ? null : body.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("الرجاء إدخال كلمة المرور الحالية");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            auditService.logAuth(user.getUsername(), "PASSWORD_FAILED", false,
                    "محاولة تغيير كلمة المرور بكلمة مرور حالية غير صحيحة");
            throw new BusinessException("كلمة المرور الحالية غير صحيحة");
        }
        passwordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("كلمة المرور الجديدة يجب أن تختلف عن كلمة المرور الحالية");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditService.log("CHANGE_PASSWORD", "User", user.getId(), user.getUsername(),
                "تغيير كلمة المرور الشخصية");
        return ApiResponse.ok("تم تغيير كلمة المرور بنجاح");
    }

    private String officeName() {
        for (String key : OFFICE_NAME_KEYS) {
            String value = settingService.get(key, "");
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return DEFAULT_OFFICE_NAME;
    }

    private static String lockUntilText(LocalDateTime until) {
        if (until == null) {
            return "";
        }
        if (until.toLocalDate().equals(LocalDate.now())) {
            return until.format(TIME_FORMAT);
        }
        return until.format(DATE_TIME_FORMAT);
    }

    private static List<String> permissionsOf(User user) {
        if (user.getRole() == null || user.getRole().getPermissions() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getRole().getPermissions());
    }

    private static Map<String, Object> userDto(User user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("fullName", user.getFullName());
        dto.put("email", user.getEmail());
        dto.put("phone", user.getPhone());
        dto.put("specialization", user.getSpecialization());
        dto.put("joinedAt", user.getJoinedAt());
        dto.put("active", user.isActive());
        dto.put("mustChangePassword", user.isMustChangePassword());
        dto.put("lastLoginAt", user.getLastLoginAt());
        if (user.getRole() != null) {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", user.getRole().getId());
            role.put("code", user.getRole().getCode());
            role.put("nameAr", user.getRole().getNameAr());
            dto.put("role", role);
        }
        return dto;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
