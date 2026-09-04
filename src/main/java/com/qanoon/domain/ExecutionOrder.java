package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** أمر تنفيذ. يمكن إصدار أكثر من أمر في نفس الوقت على نفس الملف. */
@Getter
@Setter
@Entity
@Table(name = "execution_orders")
public class ExecutionOrder extends BaseEntity {
    @Column(name = "execution_file_id", nullable = false)
    private Long executionFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 30)
    private Enums.OrderType orderType;

    @Column(name = "order_number", length = 60)
    private String orderNumber;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.OrderStatus status = Enums.OrderStatus.ACTIVE;

    /** الجهة المخاطَبة: مصرف، إدارة الإقامة، دائرة الأراضي، جهة العمل... */
    @Column(name = "target_entity", length = 200)
    private String targetEntity;

    @Column(length = 2000)
    private String details;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @ManyToOne @JoinColumn(name = "issued_by_id")
    private User issuedBy;
}
