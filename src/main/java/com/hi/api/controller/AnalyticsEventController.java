package com.hi.api.controller;

import com.hi.api.dto.request.TrackEventRequest;
import com.hi.api.exception.AnalyticsRateLimitExceededException;
import com.hi.api.model.AnalyticsEvent;
import com.hi.api.model.User;
import com.hi.api.security.ClientIpResolver;
import com.hi.api.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsEventController {

    private final AnalyticsService analyticsService;
    private final ClientIpResolver clientIpResolver;

    public AnalyticsEventController(AnalyticsService analyticsService, ClientIpResolver clientIpResolver) {
        this.analyticsService = analyticsService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/track")
    public ResponseEntity<Map<String, Object>> track(
            @Valid @RequestBody TrackEventRequest req,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {
        try {
            AnalyticsEvent event = analyticsService.trackEvent(req, user, clientIpResolver.resolve(httpRequest));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Đã ghi nhận sự kiện thành công",
                    "id", event.getId()
            ));
        } catch (AnalyticsRateLimitExceededException ex) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "success", true,
                    "message", "Đã tiếp nhận sự kiện"
            ));
        }
    }
}
