package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.NotificationService;
import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** تنبيهات المستخدم الحالي فقط. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public List<Notification> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                   @RequestParam(defaultValue = "20") int limit) {
        return notificationService.forUser(securityUtils.currentUserId(), unreadOnly, limit);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("unread", notificationService.unreadCount(securityUtils.currentUserId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse markRead(@PathVariable Long id) {
        notificationService.markRead(id, securityUtils.currentUserId());
        return ApiResponse.ok("تم");
    }

    @PostMapping("/read-all")
    public ApiResponse markAllRead() {
        notificationService.markAllRead(securityUtils.currentUserId());
        return ApiResponse.ok("تم تعليم كل التنبيهات كمقروءة");
    }
}
