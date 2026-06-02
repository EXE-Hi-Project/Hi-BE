package com.hi.api.controller;

import com.hi.api.model.User;
import com.hi.api.service.ChatBoxAIService;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private ChatBoxAIService chatService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String q, @AuthenticationPrincipal User user) {
        return chatService.chatStream(q, user.getId());
    }
}
