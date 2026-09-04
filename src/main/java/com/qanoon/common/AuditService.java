package com.qanoon.common;

import com.qanoon.domain.AuditLog;
import com.qanoon.domain.User;
import com.qanoon.repo.AuditLogRepository;
import com.qanoon.repo.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** سجل النشاطات: كل إنشاء أو تعديل أو حذف أو دخول، بالوقت والجهاز. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * يُكتب في معاملة مستقلة حتى يبقى القيد محفوظاً ولو تراجعت المعاملة الأصلية.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, Long entityId, String entityRef, String details) {
        AuditLog l = new AuditLog();
        String username = currentUsername();
        l.setUsername(username);
        l.setFullName(resolveFullName(username));
        l.setAction(action);
        l.setEntityType(entityType);
        l.setEntityId(entityId);
        l.setEntityRef(entityRef);
        l.setDetails(trim(details, 2000));
        l.setActedAt(LocalDateTime.now());
        l.setSuccess(true);
        fillRequestInfo(l);
        auditLogRepository.save(l);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuth(String username, String action, boolean success, String details) {
        AuditLog l = new AuditLog();
        l.setUsername(username == null ? "غير معروف" : username);
        l.setFullName(resolveFullName(username));
        l.setAction(action);
        l.setEntityType("AUTH");
        l.setDetails(trim(details, 2000));
        l.setActedAt(LocalDateTime.now());
        l.setSuccess(success);
        fillRequestInfo(l);
        auditLogRepository.save(l);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String username, String action, String entityType,
                                 LocalDate from, LocalDate to, int page, int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (hasText(username)) {
                ps.add(cb.like(cb.lower(root.get("username")), "%" + username.trim().toLowerCase() + "%"));
            }
            if (hasText(action)) {
                ps.add(cb.equal(root.get("action"), action.trim()));
            }
            if (hasText(entityType)) {
                ps.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("actedAt"), from.atStartOfDay()));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("actedAt"), to.plusDays(1).atStartOfDay()));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        int p = Math.max(page, 0);
        int s = size <= 0 ? 25 : Math.min(size, 200);
        return auditLogRepository.findAll(spec, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "actedAt")));
    }

    // ---------- أدوات داخلية ----------

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(String.valueOf(a.getPrincipal()))) {
            return "النظام";
        }
        return a.getName();
    }

    private String resolveFullName(String username) {
        if (username == null || username.isBlank() || "النظام".equals(username)) {
            return "النظام";
        }
        return userRepository.findByUsername(username).map(User::getFullName).orElse(username);
    }

    /** المهام المجدولة تعمل بلا طلب HTTP — لذلك كل شيء هنا محمي بفحص null. */
    private void fillRequestInfo(AuditLog l) {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                if (req != null) {
                    l.setIpAddress(trim(clientIp(req), 60));
                    l.setUserAgent(trim(req.getHeader("User-Agent"), 300));
                }
            }
        } catch (RuntimeException ignored) {
            // لا يجوز أن يُفشل تسجيلُ النشاط العمليةَ الأصلية
        }
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return comma > 0 ? fwd.substring(0, comma).trim() : fwd.trim();
        }
        return req.getRemoteAddr();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
