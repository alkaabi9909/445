package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.AuditService;
import com.qanoon.common.SecurityUtils;
import com.qanoon.common.SettingService;
import com.qanoon.domain.Permission;
import com.qanoon.domain.SystemSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** الإعدادات العامة — مدة الطعن وأيام التنبيه وغيرها. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @GetMapping
    public List<SystemSetting> list() {
        securityUtils.require(Permission.SETTINGS_MANAGE);
        return settingService.all().stream()
                .sorted(Comparator
                        .comparing((SystemSetting s) -> s.getSettingGroup() == null ? "" : s.getSettingGroup())
                        .thenComparing(SystemSetting::getSettingKey))
                .toList();
    }

    @PutMapping
    public ApiResponse update(@RequestBody Map<String, String> values) {
        securityUtils.require(Permission.SETTINGS_MANAGE);
        int changed = 0;
        for (var e : values.entrySet()) {
            settingService.set(e.getKey(), e.getValue());
            changed++;
        }
        settingService.clearCache();
        auditService.log("UPDATE", "SystemSetting", null, null,
                "تعديل " + changed + " إعداداً من شاشة الإعدادات");
        return ApiResponse.ok("تم حفظ الإعدادات (" + changed + ")");
    }
}
