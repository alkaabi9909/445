package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.AuditService;
import com.qanoon.common.BusinessException;
import com.qanoon.common.NotFoundException;
import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.Permission;
import com.qanoon.domain.Role;
import com.qanoon.domain.User;
import com.qanoon.repo.RoleRepository;
import com.qanoon.repo.UserRepository;
import com.qanoon.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** إدارة المستخدمين — تتطلب صلاحية إدارة المستخدمين والأدوار. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    /** نموذج إنشاء/تعديل المستخدم. */
    public record UserForm(String username,
                           String password,
                           String fullName,
                           String email,
                           String phone,
                           Long roleId,
                           String specialization,
                           LocalDate joinedAt,
                           Boolean active,
                           Boolean mustChangePassword) {
    }

    /** نموذج تعيين كلمة مرور جديدة. */
    public record ResetPasswordForm(String newPassword) {
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        securityUtils.require(Permission.USERS_MANAGE);
        return userRepository.findAll().stream().map(UserController::dto).toList();
    }

    @PostMapping
    @Transactional
    public ApiResponse create(@RequestBody UserForm form) {
        securityUtils.require(Permission.USERS_MANAGE);

        String username = text(form.username());
        if (username.isBlank()) {
            throw new BusinessException("اسم المستخدم مطلوب");
        }
        if (username.contains(" ")) {
            throw new BusinessException("اسم المستخدم يجب ألا يحتوي على مسافات");
        }
        if (text(form.fullName()).isBlank()) {
            throw new BusinessException("الاسم الكامل مطلوب");
        }
        if (form.roleId() == null) {
            throw new BusinessException("الدور الوظيفي مطلوب");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("اسم المستخدم مستخدم مسبقاً — اختر اسماً آخر");
        }
        passwordPolicy.validate(form.password());

        Role role = roleRepository.findById(form.roleId())
                .orElseThrow(() -> new NotFoundException("الدور الوظيفي غير موجود"));

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.password()));
        user.setFullName(text(form.fullName()));
        user.setEmail(nullIfBlank(form.email()));
        user.setPhone(nullIfBlank(form.phone()));
        user.setRole(role);
        user.setSpecialization(nullIfBlank(form.specialization()));
        user.setJoinedAt(form.joinedAt() != null ? form.joinedAt() : LocalDate.now());
        user.setActive(form.active() == null || form.active());
        user.setMustChangePassword(form.mustChangePassword() == null || form.mustChangePassword());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditService.log("CREATE", "User", user.getId(), user.getUsername(),
                "إضافة مستخدم جديد: " + user.getFullName() + " بدور " + role.getNameAr());
        return ApiResponse.ok("تمت إضافة المستخدم بنجاح", dto(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse update(@PathVariable Long id, @RequestBody UserForm form) {
        securityUtils.require(Permission.USERS_MANAGE);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
        Long currentUserId = securityUtils.currentUserId();
        boolean self = currentUserId != null && currentUserId.equals(user.getId());

        String username = text(form.username());
        if (!username.isBlank() && !username.equals(user.getUsername())) {
            if (username.contains(" ")) {
                throw new BusinessException("اسم المستخدم يجب ألا يحتوي على مسافات");
            }
            if (userRepository.existsByUsername(username)) {
                throw new BusinessException("اسم المستخدم مستخدم مسبقاً — اختر اسماً آخر");
            }
            user.setUsername(username);
        }
        if (!text(form.fullName()).isBlank()) {
            user.setFullName(text(form.fullName()));
        }
        user.setEmail(nullIfBlank(form.email()));
        user.setPhone(nullIfBlank(form.phone()));
        user.setSpecialization(nullIfBlank(form.specialization()));
        if (form.joinedAt() != null) {
            user.setJoinedAt(form.joinedAt());
        }

        // تغيير الدور — لا يجوز أن يبقى النظام بلا مدير نشط
        if (form.roleId() != null && (user.getRole() == null || !form.roleId().equals(user.getRole().getId()))) {
            Role role = roleRepository.findById(form.roleId())
                    .orElseThrow(() -> new NotFoundException("الدور الوظيفي غير موجود"));
            if (!role.has(Permission.USERS_MANAGE) && isLastActiveManager(user)) {
                throw new BusinessException("لا يمكن سحب صلاحية إدارة المستخدمين من آخر مدير نشط في النظام");
            }
            user.setRole(role);
        }

        // التعطيل — لا تعطيل للحساب الشخصي ولا لآخر مدير نشط
        if (form.active() != null && form.active() != user.isActive()) {
            if (!form.active()) {
                if (self) {
                    throw new BusinessException("لا يمكنك تعطيل حسابك الشخصي");
                }
                if (isLastActiveManager(user)) {
                    throw new BusinessException("لا يمكن تعطيل آخر مدير نشط في النظام");
                }
            }
            user.setActive(form.active());
        }

        if (form.mustChangePassword() != null) {
            user.setMustChangePassword(form.mustChangePassword());
        }

        if (form.password() != null && !form.password().isBlank()) {
            passwordPolicy.validate(form.password());
            user.setPasswordHash(passwordEncoder.encode(form.password()));
            user.setMustChangePassword(true);
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }

        userRepository.save(user);
        auditService.log("UPDATE", "User", user.getId(), user.getUsername(),
                "تعديل بيانات المستخدم: " + user.getFullName());
        return ApiResponse.ok("تم حفظ بيانات المستخدم بنجاح", dto(user));
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public ApiResponse resetPassword(@PathVariable Long id, @RequestBody ResetPasswordForm form) {
        securityUtils.require(Permission.USERS_MANAGE);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
        passwordPolicy.validate(form == null ? null : form.newPassword());

        user.setPasswordHash(passwordEncoder.encode(form.newPassword()));
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditService.log("RESET_PASSWORD", "User", user.getId(), user.getUsername(),
                "تعيين كلمة مرور جديدة للمستخدم: " + user.getFullName());
        return ApiResponse.ok("تم تعيين كلمة مرور جديدة، وسيُطلب من المستخدم تغييرها عند أول دخول");
    }

    @PostMapping("/{id}/unlock")
    @Transactional
    public ApiResponse unlock(@PathVariable Long id) {
        securityUtils.require(Permission.USERS_MANAGE);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
        if (!user.isLocked() && user.getFailedAttempts() == 0) {
            throw new BusinessException("الحساب غير مقفل");
        }
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        userRepository.save(user);

        auditService.log("UNLOCK", "User", user.getId(), user.getUsername(),
                "رفع القفل عن حساب: " + user.getFullName());
        return ApiResponse.ok("تم رفع القفل عن الحساب بنجاح", dto(user));
    }

    /** هل هذا المستخدم آخر مدير نشط (يملك صلاحية إدارة المستخدمين)؟ */
    private boolean isLastActiveManager(User target) {
        if (!target.isActive() || !target.has(Permission.USERS_MANAGE)) {
            return false;
        }
        long others = userRepository.findByActiveTrue().stream()
                .filter(u -> u.getId() != null && !u.getId().equals(target.getId()))
                .filter(u -> u.has(Permission.USERS_MANAGE))
                .count();
        return others == 0;
    }

    private static Map<String, Object> dto(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("specialization", user.getSpecialization());
        map.put("joinedAt", user.getJoinedAt());
        map.put("active", user.isActive());
        map.put("locked", user.isLocked());
        map.put("lockedUntil", user.getLockedUntil());
        map.put("failedAttempts", user.getFailedAttempts());
        map.put("mustChangePassword", user.isMustChangePassword());
        map.put("lastLoginAt", user.getLastLoginAt());
        if (user.getRole() != null) {
            map.put("roleId", user.getRole().getId());
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", user.getRole().getId());
            role.put("code", user.getRole().getCode());
            role.put("nameAr", user.getRole().getNameAr());
            map.put("role", role);
        }
        return map;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullIfBlank(String value) {
        String trimmed = text(value);
        return trimmed.isBlank() ? null : trimmed;
    }
}
