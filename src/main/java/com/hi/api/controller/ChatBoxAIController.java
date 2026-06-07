package com.hi.api.controller;

import com.hi.api.model.User;
import com.hi.api.service.ChatBoxAIService;
import com.hi.api.service.ChatContextService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/api/chat")
public class ChatBoxAIController {
    private final ChatBoxAIService chatService;
    private final ChatContextService chatContextService;

    public ChatBoxAIController(ChatBoxAIService chatService, ChatContextService chatContextService) {
        this.chatService = chatService;
        this.chatContextService = chatContextService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String q, @AuthenticationPrincipal User user) {
        String userId = user != null ? user.getId() : "anonymous";
        String context = user != null ? chatContextService.buildContext(user.getId()) : "";
        return chatService.chatStream(q, userId, context);
    }
}
