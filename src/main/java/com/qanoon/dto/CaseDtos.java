package com.qanoon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qanoon.domain.Appeal;
import com.qanoon.domain.Attachment;
import com.qanoon.domain.Communication;
import com.qanoon.domain.Enums;
import com.qanoon.domain.Hearing;
import com.qanoon.domain.LegalCase;
import com.qanoon.service.CaseService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * كائنات نقل البيانات (DTOs) لمسار القضايا: القضية، الجلسات، الأحكام،
 * الاستئناف، الإسناد، وبوابة التحويل إلى التنفيذ.
 */
public final class CaseDtos {

    private CaseDtos() {
    }

    /** طلب إنشاء أو تعديل قضية. */
    public record CaseRequest(
            String courtCaseNumber,
            String court,
            Enums.CaseType caseType,
            Long clientId,
            Long opponentId,
            Long assignedLawyerId,
            String assignmentReason,
            String subject,
            String description,
            BigDecimal claimAmount,
            Enums.CaseStatus status,
            LocalDate openedAt,
            LocalDate filedAt,
            Long sourceFinancialFileId) {
    }

    /** طلب إسناد القضية إلى محامٍ — سبب الإسناد إلزامي. */
    public record AssignRequest(Long lawyerId, String reason) {
    }

    /** طلب تسجيل أو تعديل جلسة. */
    public record HearingRequest(
            LocalDate hearingDate,
            LocalTime hearingTime,
            Enums.HearingType type,
            String court,
            String room,
            String notes,
            String result,
            Boolean attended,
            LocalDate nextHearingDate) {
    }

    /** طلب توثيق الحكم — يُحتسب عنده أجل الطعن. */
    public record JudgmentRequest(
            LocalDate judgmentDate,
            String judgmentNumber,
            Enums.JudgmentFor judgmentFor,
            BigDecimal judgmentAmount,
            String judgmentSummary,
            Boolean judgmentFinal) {
    }

    /** طلب تسجيل استئناف على القضية. */
    public record AppealRequest(
            String appealNumber,
            LocalDate filedDate,
            Enums.AppealBy filedBy,
            String court,
            String notes) {
    }

    /** طلب الفصل في الاستئناف: تم الفصل فيه أو مسحوب. */
    public record AppealDecisionRequest(
            Enums.AppealStatus status,
            LocalDate decisionDate,
            String decisionSummary) {
    }

    /** طلب إغلاق القضية وأرشفتها. */
    public record CloseRequest(String note) {
    }

    /** سطر في قائمة القضايا. */
    public record CaseRow(
            Long id,
            String caseNumber,
            String courtCaseNumber,
            String court,
            String caseType,
            String caseTypeLabel,
            String clientName,
            String opponentName,
            String assignedLawyerName,
            String subject,
            BigDecimal claimAmount,
            String status,
            String statusLabel,
            LocalDate openedAt,
            LocalDate filedAt,
            LocalDate judgmentDate,
            String judgmentNumber,
            LocalDate appealDeadline,
            boolean judgmentFinal,
            Long executionFileId,
            boolean archived) {
    }

    /** تفاصيل القضية الكاملة: القضية + الجلسات + الاستئنافات + التواصل المنقول + المرفقات + بوابة التحويل. */
    public record CaseDetail(
            @JsonProperty("case") LegalCase legalCase,
            List<Hearing> hearings,
            List<Appeal> appeals,
            List<Communication> communications,
            List<Attachment> attachments,
            CaseService.TransferCheck transferCheck) {
    }

    /** نتيجة التحويل الناجح إلى ملف تنفيذ. */
    public record TransferResult(Long executionFileId, String executionNumber) {
    }
}
