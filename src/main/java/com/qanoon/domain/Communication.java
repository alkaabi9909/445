package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** سجل التواصل والإنذارات على الملف المالي (يُنقل تلقائياً عند التصعيد). */
@Getter
@Setter
@Entity
@Table(name = "communications")
public class Communication extends BaseEntity {
    @Column(name = "financial_file_id")
    private Long financialFileId;

    @Column(name = "case_id")
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.CommType type;

    @Column(name = "comm_date", nullable = false)
    private LocalDateTime commDate = LocalDateTime.now();

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(length = 500)
    private String outcome;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    /** رقم الإنذار الرسمي إن وجد */
    @Column(name = "reference_no", length = 60)
    private String referenceNo;

    @Column(name = "copied_from_id")
    private Long copiedFromId;
}
