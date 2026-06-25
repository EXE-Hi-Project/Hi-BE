package com.hi.api.controller;

import com.hi.api.dto.request.PartnerAnswerRequest;
import com.hi.api.dto.request.PartnerMessageRequest;
import com.hi.api.model.PartnerCareSuggestion;
import com.hi.api.model.User;
import com.hi.api.service.CoupleQuestionService;
import com.hi.api.service.PartnerCareSuggestionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/partner")
@SecurityRequirement(name = "Bearer Authentication")
public class PartnerExperienceController {

    private final CoupleQuestionService questionService;
    private final PartnerCareSuggestionService careSuggestionService;

    public PartnerExperienceController(CoupleQuestionService questionService,
                                       PartnerCareSuggestionService careSuggestionService) {
        this.questionService = questionService;
        this.careSuggestionService = careSuggestionService;
    }

    @GetMapping("/questions/today")
    public ResponseEntity<Map<String, Object>> today(@AuthenticationPrincipal User user) {
        return ok("question", questionService.getToday(user.getId()));
    }

    @PostMapping("/questions/today/answer")
    public ResponseEntity<Map<String, Object>> answer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PartnerAnswerRequest request) {
        return ok("question", questionService.answerToday(user.getId(), request.getContent()));
    }

    @PostMapping("/questions/today/skip")
    public ResponseEntity<Map<String, Object>> skip(@AuthenticationPrincipal User user) {
        return ok("question", questionService.skipToday(user.getId()));
    }

    @GetMapping("/questions/history")
    public ResponseEntity<Map<String, Object>> history(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "31") int limit) {
        return ok("history", questionService.history(user.getId(), page, limit));
    }

    @GetMapping("/questions/{sessionId}")
    public ResponseEntity<Map<String, Object>> session(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId) {
        return ok("question", questionService.getSession(user.getId(), sessionId));
    }

    @PutMapping("/questions/{sessionId}/answer")
    public ResponseEntity<Map<String, Object>> answerSession(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId,
            @Valid @RequestBody PartnerAnswerRequest request) {
        return ok("question", questionService.answerSession(user.getId(), sessionId, request.getContent()));
    }

    @PostMapping("/questions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> message(
            @AuthenticationPrincipal User user,
            @PathVariable String sessionId,
            @Valid @RequestBody PartnerMessageRequest request) {
        return ok("question", questionService.addMessage(user.getId(), sessionId, request.getContent()));
    }

    @GetMapping("/care-suggestions/today")
    public ResponseEntity<Map<String, Object>> careSuggestion(@AuthenticationPrincipal User user) {
        PartnerCareSuggestion suggestion = careSuggestionService.getToday(user.getId());
        return ok("suggestion", suggestion);
    }

    private ResponseEntity<Map<String, Object>> ok(String key, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put(key, value);
        return ResponseEntity.ok(response);
    }
}
