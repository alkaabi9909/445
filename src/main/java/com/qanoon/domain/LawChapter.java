package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * عقدة في هرم القانون: باب ← فصل.
 * الأب فارغ = باب، والأب موجود = فصل داخل باب.
 */
@Getter
@Setter
@Entity
@Table(name = "law_chapters")
public class LawChapter extends BaseEntity {
    public enum Level { BAB, FASL }

    @Column(name = "law_code_id", nullable = false)
    private Long lawCodeId;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level = Level.BAB;

    @Column(name = "chapter_number", length = 40)
    private String chapterNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
