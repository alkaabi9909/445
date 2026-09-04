package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** تنبيه داخل النظام: أجل طعن، جلسة قادمة، قسط مستحق، مهمة. */
@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = @Index(name = "ix_notif_user", columnList = "user_id,is_read"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.NotificationType type = Enums.NotificationType.INFO;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    /** نوع الكيان المرتبط للانتقال المباشر */
    @Column(name = "link_type", length = 30)
    private String linkType;

    @Column(name = "link_id")
    private Long linkId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** مفتاح فريد لمنع تكرار نفس التنبيه يومياً */
    @Column(name = "dedupe_key", length = 120, unique = true)
    private String dedupeKey;
}
