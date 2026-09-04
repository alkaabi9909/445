package com.qanoon.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "installments")
public class Installment extends BaseEntity {
    @JsonIgnore
    @ManyToOne(optional = false) @JoinColumn(name = "plan_id")
    private PaymentPlan plan;

    @Column(name = "financial_file_id", nullable = false)
    private Long financialFileId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.InstallmentStatus status = Enums.InstallmentStatus.DUE;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
}
