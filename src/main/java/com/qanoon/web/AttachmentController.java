package com.qanoon.web;

import com.qanoon.common.*;
import com.qanoon.domain.*;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * المرفقات.
 * القاعدة الذهبية "كل وثيقة تتبع ملفها": لا تُسلَّم وثيقة إلا لمن يملك
 * حق الاطلاع على الملف الأب.
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final StorageService storageService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final FinancialFileRepository financialFileRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final ExecutionFileRepository executionFileRepository;
    private final ConsultationRepository consultationRepository;

    @GetMapping
    public List<Attachment> list(@RequestParam String entityType, @RequestParam Long entityId) {
        assertCanAccess(entityType, entityId);
        return storageService.list(entityType, entityId);
    }

    @PostMapping
    public ApiResponse upload(@RequestParam("file") MultipartFile file,
                              @RequestParam String entityType,
                              @RequestParam Long entityId,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String description) {
        // entityId = 0 يعني رفعاً مؤقتاً قبل إنشاء الملف (مثل مستند المطالبة عند فتح ملف مالي)
        if (entityId != null && entityId > 0) {
            assertCanAccess(entityType, entityId);
        }
        Attachment a = storageService.store(file, entityType, entityId == null ? 0L : entityId,
                category, description);
        auditService.log("UPLOAD", "Attachment", a.getId(), a.getFileName(),
                "رفع مرفق: " + a.getFileName() + (category == null ? "" : " — الفئة: " + category));
        return ApiResponse.ok("تم رفع المرفق", a);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment a = storageService.get(id);
        assertCanAccess(a.getEntityType(), a.getEntityId());
        Resource res = storageService.load(id);
        String name = a.getFileName() == null ? "مستند" : a.getFileName();
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8)
                .build();
        MediaType type = a.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(a.getContentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(type)
                .body(res);
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        Attachment a = storageService.get(id);
        assertCanAccess(a.getEntityType(), a.getEntityId());
        String name = a.getFileName();
        storageService.delete(id);
        auditService.log("DELETE", "Attachment", id, name, "حذف مرفق: " + name);
        return ApiResponse.ok("تم حذف المرفق");
    }

    // ==================== كل وثيقة تتبع ملفها ====================

    private void assertCanAccess(String entityType, Long entityId) {
        if (entityType == null || entityId == null || entityId <= 0) {
            return;
        }
        boolean allowed = switch (entityType) {
            case "FINANCIAL_FILE" -> canFinancial(entityId);
            case "CASE" -> canCase(entityId);
            case "EXECUTION" -> canExecution(entityId);
            case "CONSULTATION" -> canConsultation(entityId);
            case "ARCHIVE", "ArchiveItem" -> securityUtils.has(Permission.ARCHIVE_VIEW);
            default -> securityUtils.isManager();
        };
        if (!allowed) {
            throw new ForbiddenException("لا تملك حق الاطلاع على الملف الأب لهذه الوثيقة");
        }
    }

    private boolean canFinancial(Long id) {
        if (!securityUtils.has(Permission.FINANCIAL_VIEW) && !securityUtils.has(Permission.FINANCIAL_VIEW_ALL)) {
            return false;
        }
        if (securityUtils.has(Permission.FINANCIAL_VIEW_ALL)) {
            return true;
        }
        return financialFileRepository.findById(id)
                .map(f -> isMine(f.getAssignedLawyer()))
                .orElse(false);
    }

    private boolean canCase(Long id) {
        if (!securityUtils.has(Permission.CASE_VIEW) && !securityUtils.has(Permission.CASE_VIEW_ALL)) {
            return false;
        }
        if (securityUtils.has(Permission.CASE_VIEW_ALL)) {
            return true;
        }
        return legalCaseRepository.findById(id)
                .map(c -> isMine(c.getAssignedLawyer()))
                .orElse(false);
    }

    private boolean canExecution(Long id) {
        if (!securityUtils.has(Permission.EXECUTION_VIEW) && !securityUtils.has(Permission.EXECUTION_VIEW_ALL)) {
            return false;
        }
        if (securityUtils.has(Permission.EXECUTION_VIEW_ALL)) {
            return true;
        }
        return executionFileRepository.findById(id)
                .map(e -> isMine(e.getAssignedLawyer()))
                .orElse(false);
    }

    private boolean canConsultation(Long id) {
        if (!securityUtils.has(Permission.CONSULT_VIEW) && !securityUtils.has(Permission.CONSULT_VIEW_ALL)) {
            return false;
        }
        if (securityUtils.has(Permission.CONSULT_VIEW_ALL)) {
            return true;
        }
        return consultationRepository.findById(id)
                .map(c -> isMine(c.getConsultant()) || isMine(c.getReviewer()))
                .orElse(false);
    }

    private boolean isMine(User u) {
        User me = securityUtils.currentUserOrNull();
        return me != null && u != null && me.getId().equals(u.getId());
    }
}
