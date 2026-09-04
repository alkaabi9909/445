package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** مادة قانونية داخل باب/فصل. */
@Getter
@Setter
@Entity
@Table(name = "law_articles", indexes = @Index(name = "ix_article_chapter", columnList = "chapter_id"))
public class LawArticle extends BaseEntity {
    @Column(name = "law_code_id", nullable = false)
    private Long lawCodeId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "article_number", nullable = false, length = 40)
    private String articleNumber;

    @Column(length = 300)
    private String title;

    @Column(name = "article_text", nullable = false, length = 4000)
    private String articleText;

    @Column(length = 500)
    private String keywords;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
