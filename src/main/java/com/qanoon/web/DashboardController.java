package com.qanoon.web;

import com.qanoon.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** لوحة المعلومات الرئيسية. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardService.Dashboard dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/urgent")
    public List<DashboardService.UrgentItem> urgent() {
        return dashboardService.urgent();
    }
}
