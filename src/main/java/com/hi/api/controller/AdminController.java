package com.hi.api.controller;

import com.hi.api.dto.request.UpdateRoleRequest;
import com.hi.api.model.User;
import com.hi.api.service.AdminService;
import com.hi.api.service.ReminderService;
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

    public AdminController(AdminService adminService, ReminderService reminderService) {
        this.adminService = adminService;
        this.reminderService = reminderService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> data = adminService.getOverview();
        data.put("success", true);
        return ResponseEntity.ok(data);
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
}
