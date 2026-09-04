package com.qanoon.web;

import com.qanoon.common.ExcelExportService;
import com.qanoon.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** التقارير وتصديرها إلى Excel. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ExcelExportService excel;

    @GetMapping("/{kind}")
    public ReportService.Report report(@PathVariable String kind,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.build(kind, from, to);
    }

    @GetMapping("/{kind}/export")
    public ResponseEntity<byte[]> export(@PathVariable String kind,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportService.Report r = reportService.build(kind, from, to);
        byte[] data = excel.export(r.title(), r.headers(), r.rows());
        return AuditController.download(data, r.title() + ".xlsx");
    }
}
