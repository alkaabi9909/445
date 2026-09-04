package com.qanoon.common;

import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.qanoon.domain.Permission;

/** أدوات الوصول للمستخدم الحالي وفحص صلاحياته. */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /** المستخدم الحالي، أو استثناء إن لم تكن هناك جلسة. */
    public User currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(String.valueOf(a.getPrincipal()))) {
            throw new ForbiddenException("يجب تسجيل الدخول");
        }
        return userRepository.findByUsername(a.getName())
                .orElseThrow(() -> new ForbiddenException("يجب تسجيل الدخول"));
    }

    /** المستخدم الحالي إن وُجد، أو null (تستخدمها المهام المجدولة). */
    public User currentUserOrNull() {
        try {
            return currentUser();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Long currentUserId() {
        return currentUser().getId();
    }

    public boolean has(String permission) {
        User u = currentUserOrNull();
        return u != null && u.has(permission);
    }

    public void require(String permission) {
        if (!has(permission)) {
            String label = Permission.LABELS.getOrDefault(permission, permission);
            throw new ForbiddenException("لا تملك صلاحية: " + label);
        }
    }

    /** المدير = من يملك صلاحية إدارة المستخدمين. */
    public boolean isManager() {
        return has(Permission.USERS_MANAGE);
    }
}
