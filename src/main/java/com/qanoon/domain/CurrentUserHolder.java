package com.qanoon.domain;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** أداة مساعدة للحصول على اسم المستخدم الحالي من سياق الأمان. */
public final class CurrentUserHolder {
    private CurrentUserHolder() {}

    public static String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) return "system";
        return a.getName();
    }
}
