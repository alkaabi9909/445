package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** تشريع أو قانون في المكتبة القانونية. */
@Getter
@Setter
@Entity
@Table(name = "law_codes")
public class LawCode extends BaseEntity {
    @Column(nullable = false, length = 300)
    private String title;

    /** رقم القانون، مثال: "قانون اتحادي رقم (5) لسنة 1985" */
    @Column(name = "law_number", length = 120)
    private String lawNumber;

    @Column(name = "issue_year")
    private Integer issueYear;

    @Column(length = 150)
    private String jurisdiction;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
