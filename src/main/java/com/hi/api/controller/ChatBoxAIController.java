package com.hi.api.controller;

import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import com.hi.api.service.ChatBoxAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/chatbox")
public class ChatBoxAIController {
    @Autowired
    private ChatBoxAIService chatService;

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String q, @AuthenticationPrincipal User user) {
        return chatService.chatStream(q, user );
    }
    @GetMapping (value = "/chat/history")
    public ResponseEntity<Map<String, Object>> history(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit) {
        if (user == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        List<ChatMessage> chatHistory = chatService.getHistory(user, limit);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Lấy lịch sử trò chuyện thành công");
        response.put("data", Map.of("chatHistory", chatHistory));
        return ResponseEntity.ok(response);
    }
}
