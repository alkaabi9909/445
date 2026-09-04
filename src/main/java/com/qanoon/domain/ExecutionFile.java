package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** ملف التنفيذ: الاستيفاء الجبري للحق بعد استلام الصيغة التنفيذية. */
@Getter
@Setter
@Entity
@Table(name = "execution_files")
public class ExecutionFile extends BaseEntity {
    @Column(name = "execution_number", nullable = false, unique = true, length = 30)
    private String executionNumber;

    /** رقم ملف التنفيذ لدى محكمة التنفيذ */
    @Column(name = "court_execution_number", length = 60)
    private String courtExecutionNumber;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @ManyToOne(optional = false) @JoinColumn(name = "client_id")
    private Party client;

    @ManyToOne(optional = false) @JoinColumn(name = "debtor_id")
    private Party debtor;

    @ManyToOne @JoinColumn(name = "assigned_lawyer_id")
    private User assignedLawyer;

    @Column(length = 150)
    private String court;

    @Column(name = "judgment_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal judgmentAmount = BigDecimal.ZERO;

    @Column(name = "expenses_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expensesAmount = BigDecimal.ZERO;

    @Column(name = "collected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ExecStatus status = Enums.ExecStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt = LocalDate.now();

    /** تاريخ استلام الصيغة التنفيذية */
    @Column(name = "writ_received_at")
    private LocalDate writRecievedAt;

    @Column(name = "satisfied_at")
    private LocalDateTime satisfiedAt;

    @Column(name = "certificate_number", length = 40)
    private String certificateNumber;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(nullable = false)
    private boolean archived;

    @Column(length = 2000)
    private String notes;

    /** المتبقي = (أصل الحكم + المصروفات) − المحصّل. الإغلاق لا يجوز إلا وهو صفر. */
    @Transient
    public BigDecimal getRemainingAmount() {
        return judgmentAmount.add(expensesAmount).subtract(collectedAmount).max(BigDecimal.ZERO);
    }
}
