package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** المسار الثاني: القضية – من رفع الدعوى حتى الحكم والتحويل للتنفيذ. */
@Getter
@Setter
@Entity
@Table(name = "legal_cases")
public class LegalCase extends BaseEntity {
    @Column(name = "case_number", nullable = false, unique = true, length = 30)
    private String caseNumber;

    /** رقم القضية لدى المحكمة */
    @Column(name = "court_case_number", length = 60)
    private String courtCaseNumber;

    @Column(length = 150)
    private String court;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 30)
    private Enums.CaseType caseType = Enums.CaseType.CIVIL;

    @ManyToOne(optional = false) @JoinColumn(name = "client_id")
    private Party client;

    @ManyToOne(optional = false) @JoinColumn(name = "opponent_id")
    private Party opponent;

    @ManyToOne @JoinColumn(name = "assigned_lawyer_id")
    private User assignedLawyer;

    @Column(name = "assignment_reason", length = 500)
    private String assignmentReason;

    @Column(name = "source_financial_file_id")
    private Long sourceFinancialFileId;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(length = 3000)
    private String description;

    @Column(name = "claim_amount", precision = 18, scale = 2)
    private BigDecimal claimAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.CaseStatus status = Enums.CaseStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt = LocalDate.now();

    @Column(name = "filed_at")
    private LocalDate filedAt;

    // ---- توثيق الحكم ----
    @Column(name = "judgment_date")
    private LocalDate judgmentDate;

    @Column(name = "judgment_number", length = 60)
    private String judgmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "judgment_for", length = 20)
    private Enums.JudgmentFor judgmentFor;

    @Column(name = "judgment_amount", precision = 18, scale = 2)
    private BigDecimal judgmentAmount;

    @Column(name = "judgment_summary", length = 3000)
    private String judgmentSummary;

    /** موسوم صراحة كنهائي */
    @Column(name = "judgment_final", nullable = false)
    private boolean judgmentFinal;

    /** أجل الطعن = تاريخ الحكم + مدة الطعن القانونية */
    @Column(name = "appeal_deadline")
    private LocalDate appealDeadline;

    @Column(name = "appeal_days")
    private Integer appealDays;

    @Column(name = "execution_file_id")
    private Long executionFileId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_note", length = 1000)
    private String closureNote;

    @Column(nullable = false)
    private boolean archived;
}
