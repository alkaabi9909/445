package com.qanoon.security;

import com.qanoon.common.AuditService;
import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * سياسة قفل الحساب: بعد عدد محدد من المحاولات الفاشلة يُقفل الحساب لمدة محددة.
 * كل محاولة — ناجحة أو فاشلة أو ممنوعة — تُسجَّل في سجل النشاطات.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /** عدد المحاولات الفاشلة قبل القفل. */
    @Getter
    private final int maxFailedLogins;

    /** مدة القفل بالدقائق. */
    @Getter
    private final int lockMinutes;

    public LoginAttemptService(UserRepository userRepository,
                               AuditService auditService,
                               @Value("${qanoon.max-failed-logins:5}") int maxFailedLogins,
                               @Value("${qanoon.lock-minutes:15}") int lockMinutes) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.maxFailedLogins = maxFailedLogins > 0 ? maxFailedLogins : 5;
        this.lockMinutes = lockMinutes > 0 ? lockMinutes : 15;
    }

    /** محاولة فاشلة: يزيد العداد ويقفل الحساب عند بلوغ الحد. */
    @Transactional
    public void onFailure(String username) {
        String name = username == null ? "" : username.trim();
        Optional<User> found = userRepository.findByUsername(name);
        if (found.isEmpty()) {
            auditService.logAuth(name, "LOGIN_FAILED", false, "محاولة دخول باسم مستخدم غير موجود");
            return;
        }
        User user = found.get();
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        String details;
        if (user.getFailedAttempts() >= maxFailedLogins) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            details = "كلمة مرور غير صحيحة — تم قفل الحساب لمدة " + lockMinutes
                    + " دقيقة بعد " + user.getFailedAttempts() + " محاولات فاشلة";
        } else {
            details = "كلمة مرور غير صحيحة — المحاولة " + user.getFailedAttempts()
                    + " من " + maxFailedLogins;
        }
        userRepository.save(user);
        auditService.logAuth(name, "LOGIN_FAILED", false, details);
    }

    /** محاولة ناجحة: تصفير العداد ورفع القفل وتحديث آخر دخول. */
    @Transactional
    public void onSuccess(User user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        auditService.logAuth(user.getUsername(), "LOGIN", true, "تسجيل دخول ناجح");
    }

    /** محاولة ممنوعة قبل المصادقة (حساب موقوف أو مقفل) — تُسجَّل ولا تزيد العداد. */
    public void onBlocked(String username, String reasonAr) {
        auditService.logAuth(username == null ? "" : username.trim(), "LOGIN_BLOCKED", false, reasonAr);
    }

    /** رفع القفل يدوياً من شاشة إدارة المستخدمين. */
    @Transactional
    public void unlock(User user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    /** عدد المحاولات المتبقية قبل القفل. */
    public int remainingAttempts(User user) {
        int remaining = maxFailedLogins - user.getFailedAttempts();
        return Math.max(remaining, 0);
    }
}
