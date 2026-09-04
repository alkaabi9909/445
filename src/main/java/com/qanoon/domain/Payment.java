package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** دفعة على ملف مالي أو ملف تنفيذ. الحالة المالية لا تتغير إلا بالدفعات المؤكدة. */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {
    @Column(name = "financial_file_id")
    private Long financialFileId;

    @Column(name = "execution_file_id")
    private Long executionFileId;

    @Column(name = "installment_id")
    private Long installmentId;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 30)
    private String receiptNumber;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.PaymentStatus status = Enums.PaymentStatus.PENDING;

    /** رقم الشيك / مرجع التحويل / آخر أرقام البطاقة */
    @Column(name = "reference_no", length = 80)
    private String referenceNo;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "payer_name", length = 150)
    private String payerName;

    @Column(length = 500)
    private String notes;

    @ManyToOne @JoinColumn(name = "received_by_id")
    private User receivedBy;

    @ManyToOne @JoinColumn(name = "confirmed_by_id")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "bounce_reason", length = 300)
    private String bounceReason;
}
