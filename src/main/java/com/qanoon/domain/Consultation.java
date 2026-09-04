package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** المسار الثالث: الاستشارة القانونية – مسار مستقل بمبدأ العينية. */
@Getter
@Setter
@Entity
@Table(name = "consultations")
public class Consultation extends BaseEntity {
    @Column(name = "consultation_number", nullable = false, unique = true, length = 30)
    private String consultationNumber;

    @ManyToOne @JoinColumn(name = "client_id")
    private Party client;

    @Column(nullable = false, length = 300)
    private String subject;

    /** مجال الطلب – يُطابَق مع تخصص المستشار في التوزيع الذكي */
    @Column(length = 100)
    private String specialization;

    @Column(name = "request_text", length = 4000)
    private String requestText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.Priority priority = Enums.Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ConsultStatus status = Enums.ConsultStatus.RECEIVED;

    @ManyToOne @JoinColumn(name = "consultant_id")
    private User consultant;

    /** سبب الإسناد موثّق دائماً – يدوي أو محسوب بالدرجات */
    @Column(name = "assignment_reason", length = 600)
    private String assignmentReason;

    @Column(name = "assignment_score")
    private Double assignmentScore;

    @Column(name = "assigned_manually", nullable = false)
    private boolean assignedManually;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt = LocalDate.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** نص الرأي القانوني – يُقفل بعد التوقيع */
    @Column(name = "opinion_text", length = 4000)
    private String opinionText;

    @Column(name = "opinion_written_at")
    private LocalDateTime opinionWrittenAt;

    @ManyToOne @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;

    @ManyToOne @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne @JoinColumn(name = "signed_by_id")
    private User signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** بعد التوقيع: مقفل – لا يُعدَّل ولو من المدير */
    @Column(nullable = false)
    private boolean locked;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "sent_to", length = 200)
    private String sentTo;

    /** الاستشارة الجديدة التي تصحح رأياً موقّعاً */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "returned_count", nullable = false)
    private int returnedCount;

    /** نشطة = لم توقّع ولم تُرسل ولم تؤرشف (تدخل في حساب الحمل) */
    @Transient
    public boolean isActiveWorkload() {
        return status != Enums.ConsultStatus.SIGNED
                && status != Enums.ConsultStatus.SENT
                && status != Enums.ConsultStatus.ARCHIVED;
    }
}
