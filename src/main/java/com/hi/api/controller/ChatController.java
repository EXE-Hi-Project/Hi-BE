package com.hi.api.controller;

import com.hi.api.dto.request.SendMessageRequest;
import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import com.hi.api.service.ChatService;
import com.hi.api.service.ChatStreamService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = "Bearer Authentication")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;

    public ChatController(ChatService chatService, ChatStreamService chatStreamService) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
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

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamMessage(
            @AuthenticationPrincipal User user,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SendMessageRequest req) {
        return Mono.fromCallable(() -> chatStreamService.execute(
                        user.getId(),
                        idempotencyKey,
                        req.getContent(),
                        req.getSessionDate()
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> streamEvents(idempotencyKey, result))
                .onErrorResume(error -> Flux.just(event("error", Map.of(
                        "requestId", idempotencyKey,
                        "message", safeErrorMessage(error),
                        "retryable", !(error instanceof IllegalArgumentException)
                ))));
    }

    private Flux<ServerSentEvent<Map<String, Object>>> streamEvents(
            String requestId,
            ChatStreamService.StreamResult result) {
        List<ServerSentEvent<Map<String, Object>>> events = new ArrayList<>();
        events.add(event("message.accepted", Map.of(
                "requestId", requestId,
                "message", result.userMessage(),
                "replayed", result.replayed()
        )));
        for (String chunk : chunks(result.assistantMessage().getContent(), 42)) {
            events.add(event("assistant.delta", Map.of(
                    "requestId", requestId,
                    "delta", chunk
            )));
        }
        events.add(event("assistant.completed", Map.of(
                "requestId", requestId,
                "message", result.assistantMessage(),
                "replayed", result.replayed()
        )));
        events.add(event("usage.updated", Map.of(
                "requestId", requestId,
                "usage", result.aiUsage()
        )));
        return Flux.fromIterable(events).delayElements(Duration.ofMillis(18));
    }

    private ServerSentEvent<Map<String, Object>> event(String name, Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder()
                .event(name)
                .data(data)
                .build();
    }

    static List<String> chunks(String content, int targetSize) {
        if (content == null || content.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] words = content.split("(?<=\\s)");
        for (String word : words) {
            if (!current.isEmpty() && current.length() + word.length() > targetSize) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private String safeErrorMessage(Throwable error) {
        if (error instanceof IllegalArgumentException
                || error instanceof ChatStreamService.ChatStreamConflictException) {
            String message = error.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return "Hi AI chưa thể trả lời lúc này.";
    }
}
