package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.Permission;
import com.qanoon.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** النسخ الاحتياطي اليدوي وقائمة النسخ السابقة. */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public List<BackupService.BackupResult> list() {
        securityUtils.require(Permission.BACKUP_RUN);
        return backupService.list();
    }

    @PostMapping("/run")
    public ApiResponse run() {
        securityUtils.require(Permission.BACKUP_RUN);
        var r = backupService.runBackup("يدوي");
        return ApiResponse.ok("تم إنشاء نسخة احتياطية", r);
    }
}
