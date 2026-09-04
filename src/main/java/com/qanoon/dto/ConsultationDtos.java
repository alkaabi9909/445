package com.qanoon.dto;

import com.qanoon.domain.Attachment;
import com.qanoon.domain.Consultation;
import com.qanoon.domain.ConsultationAction;
import com.qanoon.domain.Enums;
import com.qanoon.domain.Party;
import com.qanoon.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * كائنات نقل البيانات لمسار الاستشارات القانونية (المسار الثالث).
 * كل النصوص الظاهرة للمستخدم بالعربية، ولا تُرسل الكيانات الخام إلى الواجهة.
 */
public final class ConsultationDtos {

    private ConsultationDtos() {
    }

    /* ===================== الطلبات الواردة من الواجهة ===================== */

    /** POST /api/consultations */
    public record CreateRequest(Long clientId,
                                String subject,
                                String specialization,
                                String requestText,
                                String priority,
                                LocalDate dueDate,
                                Long consultantId) {
    }

    /** POST /api/consultations/{id}/assign — consultantId فارغ يعني الإسناد التلقائي */
    public record AssignRequest(Long consultantId) {
    }

    /** POST /api/consultations/{id}/opinion */
    public record OpinionRequest(String opinionText) {
    }

    /** POST /api/consultations/{id}/review */
    public record ReviewRequest(Boolean approved, String notes) {
    }

    /** POST /api/consultations/{id}/send */
    public record SendRequest(String sentTo) {
    }

    /* ===================== الردود المرسلة للواجهة ===================== */

    public record UserRef(Long id, String fullName, String username, String specialization, String roleName) {
    }

    public record PartyRef(Long id, String name, String phone) {
    }

    /** صف في قائمة الاستشارات */
    public record ConsultationRow(Long id,
                                  String consultationNumber,
                                  PartyRef client,
                                  String subject,
                                  String specialization,
                                  String status,
                                  String statusLabel,
                                  String priority,
                                  String priorityLabel,
                                  UserRef consultant,
                                  LocalDate receivedAt,
                                  LocalDate dueDate,
                                  boolean overdue,
                                  boolean locked,
                                  boolean archived,
                                  int returnedCount,
                                  Double assignmentScore,
                                  boolean assignedManually) {
    }

    /** الاستشارة كاملة */
    public record ConsultationView(Long id,
                                   String consultationNumber,
                                   PartyRef client,
                                   String subject,
                                   String specialization,
                                   String requestText,
                                   String priority,
                                   String priorityLabel,
                                   String status,
                                   String statusLabel,
                                   UserRef consultant,
                                   String assignmentReason,
                                   Double assignmentScore,
                                   boolean assignedManually,
                                   LocalDate receivedAt,
                                   LocalDate dueDate,
                                   boolean overdue,
                                   String opinionText,
                                   LocalDateTime opinionWrittenAt,
                                   UserRef reviewer,
                                   LocalDateTime reviewedAt,
                                   String reviewNotes,
                                   UserRef approvedBy,
                                   LocalDateTime approvedAt,
                                   UserRef signedBy,
                                   LocalDateTime signedAt,
                                   boolean locked,
                                   LocalDateTime sentAt,
                                   String sentTo,
                                   Long supersedesId,
                                   String supersedesNumber,
                                   boolean archived,
                                   int returnedCount,
                                   List<String> allowedActions) {
    }

    /** سطر في سجل مراحل الاستشارة */
    public record ActionView(Long id,
                             String action,
                             String actionLabel,
                             String fromStatus,
                             String fromStatusLabel,
                             String toStatus,
                             String toStatusLabel,
                             UserRef actor,
                             LocalDateTime actedAt,
                             String notes) {
    }

    public record AttachmentView(Long id,
                                 String fileName,
                                 String contentType,
                                 long fileSize,
                                 String category,
                                 String description,
                                 LocalDateTime createdAt) {
    }

    /** GET /api/consultations/{id} */
    public record ConsultationDetail(ConsultationView consultation,
                                     List<ActionView> actions,
                                     List<AttachmentView> attachments) {
    }

    /** GET /api/consultations/suggest — مرشّح للإسناد الذكي بدرجاته التفصيلية */
    public record CandidateView(Long userId,
                                String fullName,
                                double score,
                                double specializationScore,
                                double loadScore,
                                double seniorityScore,
                                long activeCount,
                                double years,
                                String reason) {
    }

    /** POST /api/consultations/{id}/correction */
    public record CorrectionResult(Long id, String consultationNumber) {
    }

    /* ===================== أسماء عربية لخطوات السجل ===================== */

    private static final Map<String, String> ACTION_LABELS = new LinkedHashMap<>();

    static {
        ACTION_LABELS.put("RECEIVE", "استلام الطلب");
        ACTION_LABELS.put("ASSIGN", "إسناد إلى مستشار");
        ACTION_LABELS.put("START_STUDY", "بدء الدراسة");
        ACTION_LABELS.put("WRITE_OPINION", "كتابة الرأي القانوني");
        ACTION_LABELS.put("SUBMIT_REVIEW", "إرسال للمراجعة");
        ACTION_LABELS.put("APPROVE", "اعتماد الرأي");
        ACTION_LABELS.put("RETURN", "إعادة بملاحظات");
        ACTION_LABELS.put("SIGN", "التوقيع النهائي");
        ACTION_LABELS.put("SEND", "إرسال للموكل");
        ACTION_LABELS.put("ARCHIVE", "أرشفة");
        ACTION_LABELS.put("CORRECTION", "إصدار رأي تصحيحي");
    }

    public static String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        String label = ACTION_LABELS.get(action);
        return label != null ? label : action;
    }

    /* ===================== التحويلات ===================== */

    public static PartyRef partyRef(Party p) {
        if (p == null) {
            return null;
        }
        return new PartyRef(p.getId(), p.getName(), p.getPhone());
    }

    public static UserRef userRef(User u) {
        if (u == null) {
            return null;
        }
        return new UserRef(u.getId(), u.getFullName(), u.getUsername(), u.getSpecialization(),
                u.getRole() != null ? u.getRole().getNameAr() : null);
    }

    public static boolean isOverdue(Consultation c) {
        if (c == null || c.getDueDate() == null || !c.isActiveWorkload()) {
            return false;
        }
        return c.getDueDate().isBefore(LocalDate.now());
    }

    public static ConsultationRow row(Consultation c) {
        return new ConsultationRow(
                c.getId(),
                c.getConsultationNumber(),
                partyRef(c.getClient()),
                c.getSubject(),
                c.getSpecialization(),
                name(c.getStatus()),
                label(c.getStatus()),
                name(c.getPriority()),
                label(c.getPriority()),
                userRef(c.getConsultant()),
                c.getReceivedAt(),
                c.getDueDate(),
                isOverdue(c),
                c.isLocked(),
                c.isArchived(),
                c.getReturnedCount(),
                c.getAssignmentScore(),
                c.isAssignedManually());
    }

    public static ConsultationView view(Consultation c, String supersedesNumber, List<String> allowedActions) {
        return new ConsultationView(
                c.getId(),
                c.getConsultationNumber(),
                partyRef(c.getClient()),
                c.getSubject(),
                c.getSpecialization(),
                c.getRequestText(),
                name(c.getPriority()),
                label(c.getPriority()),
                name(c.getStatus()),
                label(c.getStatus()),
                userRef(c.getConsultant()),
                c.getAssignmentReason(),
                c.getAssignmentScore(),
                c.isAssignedManually(),
                c.getReceivedAt(),
                c.getDueDate(),
                isOverdue(c),
                c.getOpinionText(),
                c.getOpinionWrittenAt(),
                userRef(c.getReviewer()),
                c.getReviewedAt(),
                c.getReviewNotes(),
                userRef(c.getApprovedBy()),
                c.getApprovedAt(),
                userRef(c.getSignedBy()),
                c.getSignedAt(),
                c.isLocked(),
                c.getSentAt(),
                c.getSentTo(),
                c.getSupersedesId(),
                supersedesNumber,
                c.isArchived(),
                c.getReturnedCount(),
                allowedActions != null ? allowedActions : List.of());
    }

    public static ActionView action(ConsultationAction a) {
        return new ActionView(
                a.getId(),
                a.getAction(),
                actionLabel(a.getAction()),
                name(a.getFromStatus()),
                label(a.getFromStatus()),
                name(a.getToStatus()),
                label(a.getToStatus()),
                userRef(a.getActor()),
                a.getActedAt(),
                a.getNotes());
    }

    public static AttachmentView attachment(Attachment a) {
        return new AttachmentView(
                a.getId(),
                a.getFileName(),
                a.getContentType(),
                a.getFileSize(),
                a.getCategory(),
                a.getDescription(),
                a.getCreatedAt());
    }

    public static ConsultationDetail detail(Consultation c,
                                            String supersedesNumber,
                                            List<String> allowedActions,
                                            List<ConsultationAction> actions,
                                            List<Attachment> attachments) {
        List<ActionView> actionViews = new ArrayList<>();
        if (actions != null) {
            for (ConsultationAction a : actions) {
                actionViews.add(action(a));
            }
        }
        List<AttachmentView> attachmentViews = new ArrayList<>();
        if (attachments != null) {
            for (Attachment a : attachments) {
                attachmentViews.add(attachment(a));
            }
        }
        return new ConsultationDetail(view(c, supersedesNumber, allowedActions), actionViews, attachmentViews);
    }

    public static List<ConsultationRow> rows(List<Consultation> list) {
        List<ConsultationRow> out = new ArrayList<>();
        if (list != null) {
            for (Consultation c : list) {
                out.add(row(c));
            }
        }
        return out;
    }

    private static String name(Enum<?> e) {
        return e != null ? e.name() : null;
    }

    private static String label(Enums.Labeled e) {
        return e != null ? e.label() : null;
    }
}
