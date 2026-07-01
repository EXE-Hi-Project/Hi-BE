package com.hi.api.controller;

import com.hi.api.dto.request.UpdateRoleRequest;
import com.hi.api.dto.request.UpdateAccountStatusRequest;
import com.hi.api.dto.request.AdminUserNotificationRequest;
import com.hi.api.dto.request.AdminCampaignRequest;
import com.hi.api.dto.request.UpsertAiCostRequest;
import com.hi.api.model.User;
import com.hi.api.model.AiCostLog;
import com.hi.api.service.AdminService;
import com.hi.api.service.ReminderService;
import com.hi.api.service.AnalyticsService;
import com.hi.api.service.AdminSystemHealthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AdminService adminService;
    private final ReminderService reminderService;
    private final AnalyticsService analyticsService;
    private final AdminSystemHealthService adminSystemHealthService;

    public AdminController(AdminService adminService,
                           ReminderService reminderService,
                           AnalyticsService analyticsService,
                           AdminSystemHealthService adminSystemHealthService) {
        this.adminService = adminService;
        this.reminderService = reminderService;
        this.analyticsService = analyticsService;
        this.adminSystemHealthService = adminSystemHealthService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> data = adminService.getOverview();
        data.put("success", true);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/analytics/stats")
    public ResponseEntity<Map<String, Object>> getAnalyticsStats() {
        Map<String, Object> data = analyticsService.getAnalyticsStats();
        data.put("success", true);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/system-health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", adminSystemHealthService.getHealth()
        ));
    }

    @GetMapping("/notifications/audience-count")
    public ResponseEntity<Map<String, Object>> getNotificationAudienceCount(@RequestParam String target) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "target", target.toLowerCase(),
                        "count", adminService.countNotificationAudience(target)
                )
        ));
    }

    @PostMapping("/notifications/campaigns")
    public ResponseEntity<Map<String, Object>> sendNotificationCampaign(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody AdminCampaignRequest req,
            HttpServletRequest request) {
        Map<String, Object> result = adminService.sendNotificationCampaign(
                admin.getId(),
                req.getTarget(),
                req.getTitle(),
                req.getBody(),
                req.getActionUrl(),
                request.getRemoteAddr()
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã gửi chiến dịch thông báo",
                "data", result
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String gender) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        int safePage = Math.max(page, 1);
        Map<String, Object> data = adminService.getUsers(safePage, safeLimit, q, role, gender);
        data.put("success", true);
        return ResponseEntity.ok(data);
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @AuthenticationPrincipal User admin,
            @PathVariable String id,
            @Valid @RequestBody UpdateRoleRequest req,
            HttpServletRequest request) {

        try {
            String ipAddress = request.getRemoteAddr();
            User user = adminService.updateUserRole(admin.getId(), id, req.getRole(), ipAddress);
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Map<String, Object>> updateUserAccountStatus(
            @AuthenticationPrincipal User admin,
            @PathVariable String id,
            @Valid @RequestBody UpdateAccountStatusRequest req,
            HttpServletRequest request) {

        try {
            String ipAddress = request.getRemoteAddr();
            User user = adminService.updateUserAccountStatus(admin.getId(), id, req.getStatus(), req.getReason(), ipAddress);
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> softDeleteUser(
            @AuthenticationPrincipal User admin,
            @PathVariable String id,
            HttpServletRequest request) {

        try {
            String ipAddress = request.getRemoteAddr();
            User user = adminService.softDeleteUser(admin.getId(), id, ipAddress);
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/notifications")
    public ResponseEntity<Map<String, Object>> sendUserNotification(
            @AuthenticationPrincipal User admin,
            @PathVariable String id,
            @Valid @RequestBody AdminUserNotificationRequest req,
            HttpServletRequest request) {

        try {
            String ipAddress = request.getRemoteAddr();
            adminService.sendUserNotification(admin.getId(), id, req.getTitle(), req.getMessage(), req.getType(), ipAddress);
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã gửi thông báo cho người dùng"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/trigger-reminders")
    public ResponseEntity<Map<String, Object>> triggerReminders() {
        reminderService.generatePeriodReminders();
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã kích hoạt chạy thủ công Job nhắc nhở kỳ kinh"));
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsersCsv() {
        byte[] csvData = adminService.exportUsersCsv();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/ai-costs")
    public ResponseEntity<Map<String, Object>> getAiCostLogs() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", adminService.getAiCostLogs()
        ));
    }

    @PostMapping("/ai-costs")
    public ResponseEntity<Map<String, Object>> saveAiCostLog(@Valid @RequestBody UpsertAiCostRequest req) {
        AiCostLog log = adminService.saveAiCostLog(req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã ghi nhận chi phí AI",
                "data", log
        ));
    }

    @DeleteMapping("/ai-costs/{month}")
    public ResponseEntity<Map<String, Object>> deleteAiCostLog(@PathVariable String month) {
        adminService.deleteAiCostLog(month);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa ghi nhận chi phí AI cho tháng " + month
        ));
    }
}
