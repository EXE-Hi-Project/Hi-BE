package com.hi.api.controller;

import com.hi.api.dto.request.SendMessageRequest;
import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import com.hi.api.service.ChatService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = "Bearer Authentication")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping({"", "/", "/history"})
    public ResponseEntity<Map<String, Object>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) LocalDate sessionDate) {
        List<ChatMessage> messages = chatService.getHistory(user.getId(), sessionDate);
        return ResponseEntity.ok(Map.of("success", true, "messages", messages));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessions", chatService.getSessions(user.getId(), limit)
        ));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> sendMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SendMessageRequest req) {
        ChatService.SendResult result = chatService.sendMessage(user.getId(), req.getContent(), req.getSessionDate());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "userMessage", result.userMessage(),
                "assistantMessage", result.assistantMessage(),
                "message", result.assistantMessage(),
                "aiUsage", result.aiUsage()
        ));
    }
}
