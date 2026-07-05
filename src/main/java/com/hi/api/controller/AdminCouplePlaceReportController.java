package com.hi.api.controller;

import com.hi.api.model.CouplePlaceReport;
import com.hi.api.service.CouplePlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/couple-place-reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouplePlaceReportController {

    private final CouplePlaceService couplePlaceService;

    public AdminCouplePlaceReportController(CouplePlaceService couplePlaceService) {
        this.couplePlaceService = couplePlaceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> reports() {
        List<CouplePlaceReport> reports = couplePlaceService.adminReports();
        return ResponseEntity.ok(Map.of("success", true, "reports", reports));
    }
}
