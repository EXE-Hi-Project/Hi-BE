package com.hi.api.controller;

import com.hi.api.dto.request.UpsertHealthVideoRequest;
import com.hi.api.model.HealthVideo;
import com.hi.api.model.User;
import com.hi.api.service.HealthVideoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/health-videos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHealthVideoController {

    private final HealthVideoService healthVideoService;

    public AdminHealthVideoController(HealthVideoService healthVideoService) {
        this.healthVideoService = healthVideoService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<HealthVideo> videos = healthVideoService.getAdminVideos();
        return ResponseEntity.ok(Map.of("success", true, "videos", videos));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody UpsertHealthVideoRequest request) {
        HealthVideo video = healthVideoService.create(request, admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "video", video));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @Valid @RequestBody UpsertHealthVideoRequest request) {
        HealthVideo video = healthVideoService.update(id, request, admin.getId());
        return ResponseEntity.ok(Map.of("success", true, "video", video));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> archive(@PathVariable Long id) {
        HealthVideo video = healthVideoService.archive(id);
        return ResponseEntity.ok(Map.of("success", true, "video", video));
    }
}
