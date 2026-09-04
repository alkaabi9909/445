package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** المسار الأول: الملف المالي – نقطة الدخول للتحصيل الودي. */
@Getter
@Setter
@Entity
@Table(name = "financial_files")
public class FinancialFile extends BaseEntity {
    @Column(name = "file_number", nullable = false, unique = true, length = 30)
    private String fileNumber;

    @ManyToOne(optional = false) @JoinColumn(name = "client_id")
    private Party client;

    @ManyToOne(optional = false) @JoinColumn(name = "debtor_id")
    private Party debtor;

    @ManyToOne @JoinColumn(name = "assigned_lawyer_id")
    private User assignedLawyer;

    @Column(name = "claim_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal claimAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "exempted_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal exemptedAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String subject;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.FileStatus status = Enums.FileStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt = LocalDate.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_type", length = 30)
    private Enums.ClosureType closureType;

    @Column(name = "closure_note", length = 2000)
    private String closureNote;

    @ManyToOne @JoinColumn(name = "closed_by_id")
    private User closedBy;

    /** للإعفاء: من وافق من المستوى الأعلى */
    @ManyToOne @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "escalated_case_id")
    private Long escalatedCaseId;

    @Column(nullable = false)
    private boolean archived;

    @Transient
    public BigDecimal getRemainingAmount() {
        return claimAmount.subtract(paidAmount).subtract(exemptedAmount).max(BigDecimal.ZERO);
    }
}
