package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "hearings")
public class Hearing extends BaseEntity {
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "hearing_date", nullable = false)
    private LocalDate hearingDate;

    @Column(name = "hearing_time")
    private LocalTime hearingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.HearingType type = Enums.HearingType.OTHER;

    @Column(length = 150)
    private String court;

    @Column(length = 60)
    private String room;

    @Column(length = 2000)
    private String notes;

    /** ما جرى في الجلسة */
    @Column(length = 2000)
    private String result;

    @Column(nullable = false)
    private boolean attended;

    @Column(name = "next_hearing_date")
    private LocalDate nextHearingDate;
}
