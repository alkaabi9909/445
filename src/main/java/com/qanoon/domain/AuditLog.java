package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** سجل النشاطات: كل إنشاء أو تعديل أو حذف أو دخول، بالوقت والجهاز. */
@Getter
@Setter
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "ix_audit_time", columnList = "acted_at"),
        @Index(name = "ix_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "ix_audit_user", columnList = "username")
})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "full_name", length = 150)
    private String fullName;

    /** CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED, LOGOUT, TRANSFER, CONFIRM, CANCEL, SIGN ... */
    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_ref", length = 60)
    private String entityRef;

    @Column(length = 2000)
    private String details;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean success = true;
}
