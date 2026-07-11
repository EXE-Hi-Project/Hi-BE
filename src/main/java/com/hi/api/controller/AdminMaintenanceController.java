package com.hi.api.controller;

import com.hi.api.dto.request.UpdateMaintenanceRequest;
import com.hi.api.model.User;
import com.hi.api.service.SystemMaintenanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {
    private final SystemMaintenanceService service;

    public AdminMaintenanceController(SystemMaintenanceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ok(service.adminStatus());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody UpdateMaintenanceRequest request,
            HttpServletRequest servletRequest) {
        return ok(service.update(admin.getId(), servletRequest.getRemoteAddr(), request));
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable(
            @AuthenticationPrincipal User admin,
            HttpServletRequest servletRequest) {
        return ok(service.disable(admin.getId(), servletRequest.getRemoteAddr()));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
