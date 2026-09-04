package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** سجل مراحل الاستشارة: من فعل ماذا ومتى ولماذا. */
@Getter
@Setter
@Entity
@Table(name = "consultation_actions")
public class ConsultationAction extends BaseEntity {
    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    /** ASSIGN, START_STUDY, SUBMIT_REVIEW, RETURN, APPROVE, SIGN, SEND, ARCHIVE */
    @Column(nullable = false, length = 30)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private Enums.ConsultStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private Enums.ConsultStatus toStatus;

    @ManyToOne @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt = LocalDateTime.now();

    @Column(length = 2000)
    private String notes;
}
