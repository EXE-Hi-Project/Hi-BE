package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.repository.ChatRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ChatRepository chatRepository;
    private final ChatBoxAIService chatBoxAIService;
    private final ChatContextService chatContextService;

    public ChatService(ChatRepository chatRepository,
                       ChatBoxAIService chatBoxAIService,
                       ChatContextService chatContextService) {
        this.chatRepository = chatRepository;
        this.chatBoxAIService = chatBoxAIService;
        this.chatContextService = chatContextService;
    }

    public List<ChatMessage> getHistory(String userId, LocalDate requestedDate) {
        LocalDate sessionDate = defaultSessionDate(requestedDate);
        List<ChatMessage> currentSession = chatRepository.findByUserIdAndSessionDateOrderByCreatedAtAsc(userId, sessionDate);

        Instant start = sessionDate.atStartOfDay(APP_ZONE).toInstant();
        Instant end = sessionDate.plusDays(1).atStartOfDay(APP_ZONE).toInstant();
        List<ChatMessage> legacyMessages = chatRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(userId, start, end);

        Map<String, ChatMessage> byId = new LinkedHashMap<>();
        currentSession.forEach(message -> byId.put(message.getId(), message));
        legacyMessages.stream()
                .filter(message -> message.getSessionDate() == null || sessionDate.equals(message.getSessionDate()))
                .forEach(message -> byId.putIfAbsent(message.getId(), message));

        return byId.values().stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<ChatSessionSummary> getSessions(String userId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 60));
        int sampleSize = Math.max(120, limit * 40);
        List<ChatMessage> latest = chatRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, sampleSize));

        Map<LocalDate, SessionAccumulator> grouped = new LinkedHashMap<>();
        for (ChatMessage message : latest) {
            LocalDate date = resolveSessionDate(message);
            SessionAccumulator accumulator = grouped.computeIfAbsent(date, SessionAccumulator::new);
            accumulator.count++;
            if (message.getCreatedAt() != null
                    && (accumulator.lastMessageAt == null || message.getCreatedAt().isAfter(accumulator.lastMessageAt))) {
                accumulator.lastMessageAt = message.getCreatedAt();
            }
            if ("user".equalsIgnoreCase(message.getRole()) && accumulator.title == null) {
                accumulator.title = titleFrom(message.getContent());
            }
        }

        return grouped.values().stream()
                .sorted(Comparator.comparing(SessionAccumulator::lastMessageAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .map(accumulator -> new ChatSessionSummary(
                        accumulator.sessionDate,
                        accumulator.title == null ? "Cuộc trò chuyện với Hi AI" : accumulator.title,
                        accumulator.count,
                        accumulator.lastMessageAt
                ))
                .toList();
    }

    public SendResult sendMessage(String userId, String content, LocalDate requestedDate) {
        String cleanContent = content.trim();
        LocalDate sessionDate = defaultSessionDate(requestedDate);
        String sessionTitle = titleFrom(cleanContent);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(cleanContent);
        userMessage.setSessionDate(sessionDate);
        userMessage.setSessionTitle(sessionTitle);
        userMessage.setCreatedAt(Instant.now());
        ChatMessage savedUserMessage = chatRepository.save(userMessage);

        String context = chatContextService.buildContext(userId);
        String answer = chatBoxAIService.chatOnce(cleanContent, userId, context);
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        assistantMessage.setSessionDate(sessionDate);
        assistantMessage.setSessionTitle(sessionTitle);
        assistantMessage.setCreatedAt(Instant.now());
        ChatMessage savedAssistantMessage = chatRepository.save(assistantMessage);
        return new SendResult(savedUserMessage, savedAssistantMessage);
    }

    private LocalDate defaultSessionDate(LocalDate requestedDate) {
        return requestedDate != null ? requestedDate : LocalDate.now(APP_ZONE);
    }

    private LocalDate resolveSessionDate(ChatMessage message) {
        if (message.getSessionDate() != null) {
            return message.getSessionDate();
        }
        if (message.getCreatedAt() != null) {
            return message.getCreatedAt().atZone(APP_ZONE).toLocalDate();
        }
        return LocalDate.now(APP_ZONE);
    }

    private String titleFrom(String content) {
        if (content == null || content.isBlank()) {
            return "Cuộc trò chuyện với Hi AI";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 70 ? normalized : normalized.substring(0, 67) + "...";
    }

    private static class SessionAccumulator {
        private final LocalDate sessionDate;
        private String title;
        private int count;
        private Instant lastMessageAt;

        private SessionAccumulator(LocalDate sessionDate) {
            this.sessionDate = sessionDate;
        }

        private Instant lastMessageAt() {
            return lastMessageAt;
        }
    }

    public record SendResult(ChatMessage userMessage, ChatMessage assistantMessage) {
    }

    public record ChatSessionSummary(LocalDate sessionDate, String title, int messageCount, Instant lastMessageAt) {
    }
}
