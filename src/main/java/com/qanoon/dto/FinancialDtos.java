package com.qanoon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * سجلات الطلبات والردود الخاصة بشريحة الملف المالي والدفعات والتقسيط.
 * حقول التعدادات تُرسَل كنصوص (اسم القيمة) ويُترجمها الخادم برسائل عربية عند الخطأ.
 */
public final class FinancialDtos {

    private FinancialDtos() {
    }

    // ------------------------------------------------------------------
    // الطلبات
    // ------------------------------------------------------------------

    /** إنشاء/تعديل ملف مالي. عند الإنشاء يجب أن تحتوي attachmentIds على مستند فئته «مطالبة». */
    public record FinancialFileRequest(
            Long clientId,
            Long debtorId,
            Long assignedLawyerId,
            BigDecimal claimAmount,
            String subject,
            String description,
            LocalDate openedAt,
            List<Long> attachmentIds) {
    }

    /** تسجيل تواصل أو إنذار على الملف المالي. */
    public record CommunicationRequest(
            String type,
            String summary,
            String outcome,
            String contactPerson,
            String referenceNo,
            LocalDateTime commDate) {
    }

    /** إنشاء خطة تقسيط جديدة (تُلغي الخطة النشطة السابقة). */
    public record PaymentPlanRequest(
            BigDecimal totalAmount,
            Integer installmentsCount,
            LocalDate startDate,
            Integer intervalMonths,
            String paymentMethod,
            String notes) {
    }

    /** تسجيل دفعة على الملف المالي — تُنشأ دائماً بحالة «بانتظار التأكيد». */
    public record PaymentRequest(
            BigDecimal amount,
            LocalDate paymentDate,
            String method,
            String referenceNo,
            String bankName,
            String payerName,
            Long installmentId,
            String notes) {
    }

    /** سبب إلزامي: ارتجاع شيك أو إلغاء قسط. */
    public record ReasonRequest(String reason) {
    }

    /** إنهاء الملف المالي بإحدى الوجهات الخمس. */
    public record CloseRequest(
            String closureType,
            String note,
            Long approvedById) {
    }

    // ------------------------------------------------------------------
    // الردود
    // ------------------------------------------------------------------

    /** صف الملف المالي في القوائم وفي رأس صفحة التفاصيل. */
    public record FinancialFileRow(
            Long id,
            String fileNumber,
            Long clientId,
            String clientName,
            Long debtorId,
            String debtorName,
            Long assignedLawyerId,
            String assignedLawyerName,
            BigDecimal claimAmount,
            BigDecimal paidAmount,
            BigDecimal exemptedAmount,
            BigDecimal remainingAmount,
            String subject,
            String description,
            String status,
            String statusLabel,
            LocalDate openedAt,
            LocalDateTime closedAt,
            String closureType,
            String closureTypeLabel,
            String closureNote,
            String closedByName,
            String approvedByName,
            Long escalatedCaseId,
            boolean archived) {
    }

    public record PaymentRow(
            Long id,
            Long financialFileId,
            Long executionFileId,
            String receiptNumber,
            BigDecimal amount,
            LocalDate paymentDate,
            String method,
            String methodLabel,
            String status,
            String statusLabel,
            String referenceNo,
            String bankName,
            String payerName,
            String notes,
            Long installmentId,
            Integer installmentSeq,
            String receivedByName,
            String confirmedByName,
            LocalDateTime confirmedAt,
            LocalDateTime bouncedAt,
            String bounceReason) {
    }

    public record InstallmentRow(
            Long id,
            Long planId,
            int seq,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            String status,
            String statusLabel,
            String cancelReason,
            boolean overdue) {
    }

    public record PaymentPlanRow(
            Long id,
            BigDecimal totalAmount,
            int installmentsCount,
            LocalDate startDate,
            int intervalMonths,
            String paymentMethod,
            String paymentMethodLabel,
            String notes,
            boolean active,
            BigDecimal paidTotal,
            BigDecimal remainingTotal) {
    }

    public record CommunicationRow(
            Long id,
            String type,
            String typeLabel,
            LocalDateTime commDate,
            String summary,
            String outcome,
            String contactPerson,
            String referenceNo,
            String recordedBy) {
    }

    public record AttachmentRow(
            Long id,
            String fileName,
            String contentType,
            long fileSize,
            String category,
            String description,
            LocalDateTime createdAt) {
    }

    /** رد GET /api/financial/{id} — الملف مع كل ما يتبعه. */
    public record FinancialFileDetail(
            FinancialFileRow file,
            List<PaymentRow> payments,
            PaymentPlanRow plan,
            List<InstallmentRow> installments,
            List<CommunicationRow> communications,
            List<AttachmentRow> attachments,
            BigDecimal remainingAmount,
            boolean editable,
            boolean paymentsAllowed) {
    }

    /** رد الإغلاق — caseId و caseNumber يُملآن في حالة التصعيد فقط. */
    public record CloseResult(
            String message,
            String closureType,
            String closureTypeLabel,
            Long caseId,
            String caseNumber) {
    }
}
