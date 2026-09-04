package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * عنصر في الأرشيف الشامل: حكم، رأي، نموذج، مرجع، تعليق،
 * أو ملف مغلق (مالي/قضية/تنفيذ/استشارة) مع الإشارة لمصدره.
 */
@Getter
@Setter
@Entity
@Table(name = "archive_items", indexes = {
        @Index(name = "ix_arch_type", columnList = "item_type"),
        @Index(name = "ix_arch_source", columnList = "source_type,source_id")
})
public class ArchiveItem extends BaseEntity {
    @Column(name = "reference_no", nullable = false, unique = true, length = 40)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private Enums.ArchiveType itemType;

    @Column(nullable = false, length = 400)
    private String title;

    @Column(length = 4000)
    private String content;

    @Column(length = 500)
    private String summary;

    @Column(length = 500)
    private String keywords;

    @Column(length = 100)
    private String category;

    @Column(name = "item_date")
    private LocalDate itemDate;

    /** FINANCIAL_FILE / CASE / EXECUTION / CONSULTATION */
    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_number", length = 40)
    private String sourceNumber;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "court_name", length = 150)
    private String courtName;

    @Column(name = "law_code_id")
    private Long lawCodeId;

    @Column(nullable = false)
    private boolean confidential;
}
