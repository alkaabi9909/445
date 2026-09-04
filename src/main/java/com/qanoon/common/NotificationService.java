package com.qanoon.common;

import com.qanoon.domain.Enums;
import com.qanoon.domain.Notification;
import com.qanoon.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** التنبيهات داخل النظام مع منع تكرار نفس التنبيه. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;

    @Transactional
    public void push(Long userId, Enums.NotificationType type, String title, String message,
                     String linkType, Long linkId, LocalDate dueDate, String dedupeKey) {
        if (userId == null) {
            return;
        }
        if (dedupeKey != null && !dedupeKey.isBlank() && repo.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type == null ? Enums.NotificationType.INFO : type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLinkType(linkType);
        n.setLinkId(linkId);
        n.setDueDate(dueDate);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        n.setDedupeKey(dedupeKey == null || dedupeKey.isBlank() ? null : dedupeKey);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> forUser(Long userId, boolean unreadOnly, int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 200);
        PageRequest page = PageRequest.of(0, size);
        return unreadOnly
                ? repo.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
                : repo.findByUserIdOrderByCreatedAtDesc(userId, page);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        repo.findByIdAndUserId(id, userId).ifPresent(n -> {
            n.setRead(true);
            repo.save(n);
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> list = repo.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, 500));
        list.forEach(n -> n.setRead(true));
        repo.saveAll(list);
    }
}
