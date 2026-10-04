package com.markingsystem.controller.admin;

import com.markingsystem.dto.AdminDashboardOverviewDTO;
import com.markingsystem.dto.AdminDashboardPortraitOverviewDTO;
import com.markingsystem.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId) {
        AdminDashboardOverviewDTO data = adminDashboardService.buildOverview(
                role,
                teacherId == null || teacherId.isBlank() ? null : Long.parseLong(teacherId)
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", data
        ));
    }

    @GetMapping("/portrait-overview")
    public ResponseEntity<Map<String, Object>> portraitOverview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId) {
        AdminDashboardPortraitOverviewDTO data = adminDashboardService.buildPortraitOverview(
                role,
                teacherId == null || teacherId.isBlank() ? null : Long.parseLong(teacherId)
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", data
        ));
    }
}
