package com.qanoon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * كائنات نقل البيانات الخاصة بمسار التنفيذ:
 * ملف التنفيذ، أوامر التنفيذ الخمسة، الدفعات، بوابة الاستيفاء وشهادة الاستيفاء.
 */
public final class ExecutionDtos {
    private ExecutionDtos() {}

    // ---------------------------------------------------------------- طلبات

    /** تعديل بيانات ملف التنفيذ — PUT /api/executions/{id} */
    public record ExecutionRequest(
            String courtExecutionNumber,
            String court,
            Long assignedLawyerId,
            BigDecimal expensesAmount,
            LocalDate writRecievedAt,
            String notes
    ) {}

    /** إصدار أمر تنفيذ — POST /api/executions/{id}/orders */
    public record OrderRequest(
            String orderType,
            String orderNumber,
            String targetEntity,
            String details,
            LocalDate issuedDate
    ) {}

    /** إلغاء أمر تنفيذ بسبب إلزامي — POST /api/execution-orders/{id}/cancel */
    public record CancelRequest(String reason) {}

    /** تسجيل دفعة على ملف تنفيذ — POST /api/executions/{id}/payments */
    public record ExecutionPaymentRequest(
            BigDecimal amount,
            LocalDate paymentDate,
            String method,
            String referenceNo,
            String bankName,
            String payerName,
            String notes
    ) {}

    // ---------------------------------------------------------------- عناصر العرض

    public record ExecutionOrderDto(
            Long id,
            Long executionFileId,
            String orderType,
            String orderTypeLabel,
            String orderNumber,
            LocalDate issuedDate,
            String status,
            String statusLabel,
            String targetEntity,
            String details,
            LocalDateTime cancelledAt,
            String cancelReason,
            String issuedByName,
            boolean active
    ) {}

    public record ExecutionPaymentDto(
            Long id,
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
            String receivedByName,
            String confirmedByName,
            LocalDateTime confirmedAt
    ) {}

    public record AttachmentDto(
            Long id,
            String fileName,
            String contentType,
            long fileSize,
            String category,
            String description,
            LocalDateTime createdAt
    ) {}

    /** سطر في الخط الزمني للملف — يبيّن آخر إجراء اتُّخذ ومَن اتخذه ومتى. */
    public record TimelineEntry(
            LocalDateTime at,
            String kind,
            String kindLabel,
            String title,
            String detail,
            String status,
            String statusLabel,
            Long refId
    ) {}

    /** بيانات القضية الأصلية التي صدر عنها ملف التنفيذ. */
    public record CaseBrief(
            Long id,
            String caseNumber,
            String courtCaseNumber,
            String court,
            String caseType,
            String caseTypeLabel,
            String subject,
            String status,
            String statusLabel,
            LocalDate judgmentDate,
            String judgmentNumber,
            String judgmentFor,
            String judgmentForLabel,
            BigDecimal judgmentAmount,
            String judgmentSummary,
            boolean judgmentFinal,
            String assignedLawyerName
    ) {}

    /** نتيجة بوابة الاستيفاء — GET /api/executions/{id}/satisfaction-check */
    public record SatisfactionCheck(
            boolean allowed,
            BigDecimal remaining,
            int activeOrders,
            List<String> blockers
    ) {}

    /** نتيجة الاستيفاء — POST /api/executions/{id}/satisfy */
    public record SatisfyResult(
            String certificateNumber,
            Long executionFileId,
            String executionNumber,
            LocalDateTime satisfiedAt,
            int cancelledOrders,
            String message
    ) {}

    /** شهادة الاستيفاء المرقمة — GET /api/executions/{id}/certificate */
    public record CertificateDto(
            String certificateNumber,
            String officeName,
            Long executionFileId,
            String executionNumber,
            String courtExecutionNumber,
            String court,
            Long caseId,
            String caseNumber,
            String judgmentNumber,
            LocalDate judgmentDate,
            String creditorName,
            String creditorIdNumber,
            String debtorName,
            String debtorIdNumber,
            BigDecimal judgmentAmount,
            BigDecimal expensesAmount,
            BigDecimal totalAmount,
            BigDecimal collectedAmount,
            String currency,
            LocalDate satisfactionDate,
            LocalDateTime satisfiedAt,
            LocalDate issueDate,
            String issuedByName,
            String statement
    ) {}

    /** سطر قائمة ملفات التنفيذ / رأس صفحة الملف. */
    public record ExecutionSummary(
            Long id,
            String executionNumber,
            String courtExecutionNumber,
            Long caseId,
            String caseNumber,
            String clientName,
            String debtorName,
            Long assignedLawyerId,
            String assignedLawyerName,
            String court,
            BigDecimal judgmentAmount,
            BigDecimal expensesAmount,
            BigDecimal collectedAmount,
            BigDecimal remainingAmount,
            BigDecimal totalAmount,
            String status,
            String statusLabel,
            LocalDate openedAt,
            LocalDate writRecievedAt,
            LocalDateTime satisfiedAt,
            String certificateNumber,
            LocalDateTime closedAt,
            boolean archived,
            String notes,
            int activeOrders,
            int ordersCount
    ) {}

    /** GET /api/executions/{id} — الملف + الأوامر + الدفعات + المرفقات + فحص الاستيفاء + القضية الأصلية. */
    public record ExecutionDetail(
            ExecutionSummary file,
            List<ExecutionOrderDto> orders,
            List<ExecutionPaymentDto> payments,
            List<AttachmentDto> attachments,
            List<TimelineEntry> timeline,
            SatisfactionCheck satisfaction,
            CaseBrief sourceCase,
            boolean canManage
    ) {}
}
