package com.hi.api.controller;

import com.hi.api.service.SystemMaintenanceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/maintenance")
public class SystemMaintenanceController {
    private final SystemMaintenanceService service;

    public SystemMaintenanceController(SystemMaintenanceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("success", true, "data", service.publicStatus()));
    }
}
