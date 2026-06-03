package com.hi.api.controller;

import com.hi.api.model.HealthVideo;
import com.hi.api.model.User;
import com.hi.api.service.HealthVideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health-videos")
public class HealthVideoController {

    private final HealthVideoService healthVideoService;

    public HealthVideoController(HealthVideoService healthVideoService) {
        this.healthVideoService = healthVideoService;
    }

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> recommendations(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "6") int limit) {
        List<HealthVideo> videos = healthVideoService.getRecommendations(user, limit);
        return ResponseEntity.ok(Map.of("success", true, "videos", videos));
    }
}
