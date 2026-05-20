package com.hi.api.controller;

import com.hi.api.dto.request.UpdateRoleRequest;
import com.hi.api.model.User;
import com.hi.api.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
            @PathVariable String id,
            @Valid @RequestBody UpdateRoleRequest req) {
        User user = adminService.updateUserRole(id, req.getRole());
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }
}
