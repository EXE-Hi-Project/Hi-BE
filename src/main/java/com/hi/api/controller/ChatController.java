package com.hi.api.controller;

import com.hi.api.dto.request.SendMessageRequest;
import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import com.hi.api.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // GET /api/chat  (matches FE: api.get('/chat'))
    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> getHistory(@AuthenticationPrincipal User user) {
        List<ChatMessage> messages = chatService.getHistory(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "messages", messages));
    }

    // POST /api/chat  (matches FE: api.post('/chat', { content }))
    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> sendMessage(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SendMessageRequest req) {
        try {
            ChatMessage message = chatService.sendMessage(user.getId(), req.getContent());
            return ResponseEntity.ok(Map.of("success", true, "message", message));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Lỗi khi gửi tin nhắn: " + e.getMessage()));
        }
    }
}
