package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** بحث محفوظ أو أحد آخر خمسة بحوث للمستخدم. */
@Getter
@Setter
@Entity
@Table(name = "saved_searches")
public class SavedSearch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String name;

    @Column(name = "query_text", length = 500)
    private String queryText;

    /** الفلاتر مخزّنة كـ JSON نصي */
    @Column(name = "filters_json", length = 2000)
    private String filtersJson;

    /** true = بحث محفوظ بالاسم، false = من آخر خمسة بحوث */
    @Column(name = "is_saved", nullable = false)
    private boolean saved;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
