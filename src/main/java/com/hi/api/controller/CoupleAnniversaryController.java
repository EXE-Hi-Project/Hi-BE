package com.hi.api.controller;

import com.hi.api.dto.request.UpdateCoupleStartDateRequest;
import com.hi.api.dto.request.UpsertCoupleAnniversaryEventRequest;
import com.hi.api.model.User;
import com.hi.api.service.CoupleAnniversaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/partner/anniversaries")
@SecurityRequirement(name = "Bearer Authentication")
public class CoupleAnniversaryController {

    private final CoupleAnniversaryService service;

    public CoupleAnniversaryController(CoupleAnniversaryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnniversaries(@AuthenticationPrincipal User user) {
        Map<String, Object> data = service.getAnniversaries(user.getId());
        return ok("anniversaries", data);
    }

    @PutMapping("/start-date")
    public ResponseEntity<Map<String, Object>> updateStartDate(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateCoupleStartDateRequest request) {
        Map<String, Object> data = service.updateStartDate(user.getId(), request);
        return ok("anniversaries", data);
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> createEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpsertCoupleAnniversaryEventRequest request) {
        Map<String, Object> data = service.createEvent(user.getId(), request);
        return ok("anniversaries", data);
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<Map<String, Object>> updateEvent(
            @AuthenticationPrincipal User user,
            @PathVariable String eventId,
            @Valid @RequestBody UpsertCoupleAnniversaryEventRequest request) {
        Map<String, Object> data = service.updateEvent(user.getId(), eventId, request);
        return ok("anniversaries", data);
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Map<String, Object>> deleteEvent(
            @AuthenticationPrincipal User user,
            @PathVariable String eventId) {
        Map<String, Object> data = service.deleteEvent(user.getId(), eventId);
        return ok("anniversaries", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String key, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put(key, value);
        return ResponseEntity.ok(response);
    }
}
