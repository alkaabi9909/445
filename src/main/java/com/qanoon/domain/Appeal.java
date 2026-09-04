package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * استئناف مسجل على قضية.
 * وجود استئناف مُفعّل (PENDING) يمنع التحويل للتنفيذ نهائياً — حتى بعد انقضاء الأجل.
 */
@Getter
@Setter
@Entity
@Table(name = "appeals")
public class Appeal extends BaseEntity {
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "appeal_number", length = 60)
    private String appealNumber;

    @Column(name = "filed_date", nullable = false)
    private LocalDate filedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "filed_by", nullable = false, length = 20)
    private Enums.AppealBy filedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.AppealStatus status = Enums.AppealStatus.PENDING;

    @Column(length = 150)
    private String court;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "decision_summary", length = 2000)
    private String decisionSummary;

    @Column(length = 1000)
    private String notes;

    /** الاستئناف المُفعّل = قيد النظر. هذا هو المانع الصريح للتحويل. */
    @Transient
    public boolean isActive() {
        return status == Enums.AppealStatus.PENDING;
    }
}
