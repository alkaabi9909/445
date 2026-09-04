package com.qanoon.web;

import com.qanoon.common.AuditService;
import com.qanoon.common.ExcelExportService;
import com.qanoon.common.PageResult;
import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.AuditLog;
import com.qanoon.domain.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** سجل النشاطات: كل عملية بالوقت والجهاز، وكل محاولة دخول ناجحة أو فاشلة. */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final ExcelExportService excel;
    private final SecurityUtils securityUtils;

    @GetMapping
    public PageResult search(@RequestParam(required = false) String username,
                             @RequestParam(required = false) String action,
                             @RequestParam(required = false) String entityType,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "25") int size) {
        securityUtils.require(Permission.AUDIT_VIEW);
        return PageResult.of(auditService.search(username, action, entityType, from, to, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String username,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        securityUtils.require(Permission.AUDIT_VIEW);
        var rows = new ArrayList<List<Object>>();
        for (AuditLog l : auditService.search(username, action, entityType, from, to, 0, 5000).getContent()) {
            rows.add(List.of(
                    l.getActedAt() == null ? "" : l.getActedAt(),
                    nz(l.getFullName()),
                    nz(l.getUsername()),
                    nz(l.getAction()),
                    nz(l.getEntityType()),
                    nz(l.getEntityRef()),
                    nz(l.getDetails()),
                    nz(l.getIpAddress()),
                    l.isSuccess() ? "ناجح" : "فاشل"));
        }
        byte[] data = excel.export("سجل النشاطات",
                List.of("الوقت", "الاسم", "المستخدم", "الإجراء", "الكيان", "المرجع", "التفاصيل", "عنوان الجهاز", "النتيجة"),
                rows);
        return download(data, "سجل-النشاطات.xlsx");
    }

    static ResponseEntity<byte[]> download(byte[] data, String filenameAr) {
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(filenameAr, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
