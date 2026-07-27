package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.model.ChatStreamRequest;
import com.hi.api.repository.ChatRepository;
import com.hi.api.repository.ChatStreamRequestRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Service
public class ChatStreamService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

    private final ChatStreamRequestRepository requestRepository;
    private final ChatRepository chatRepository;
    private final ChatService chatService;

    public ChatStreamService(ChatStreamRequestRepository requestRepository,
                             ChatRepository chatRepository,
                             ChatService chatService) {
        this.requestRepository = requestRepository;
        this.chatRepository = chatRepository;
        this.chatService = chatService;
    }

    public StreamResult execute(String userId,
                                String idempotencyKey,
                                String content,
                                LocalDate sessionDate) {
        String cleanKey = normalizeKey(idempotencyKey);
        ChatStreamRequest request = new ChatStreamRequest();
        request.setUserId(userId);
        request.setIdempotencyKey(cleanKey);
        request.setSessionDate(sessionDate);
        request.setStatus(ChatStreamRequest.Status.PENDING);

        try {
            request = requestRepository.save(request);
        } catch (DuplicateKeyException duplicate) {
            return replayExisting(userId, cleanKey);
        }

        try {
            ChatService.SendResult result = chatService.sendMessage(userId, content, sessionDate);
            request.setStatus(ChatStreamRequest.Status.COMPLETED);
            request.setUserMessageId(result.userMessage().getId());
            request.setAssistantMessageId(result.assistantMessage().getId());
            requestRepository.save(request);
            return new StreamResult(
                    result.userMessage(),
                    result.assistantMessage(),
                    result.aiUsage(),
                    false
            );
        } catch (RuntimeException exception) {
            request.setStatus(ChatStreamRequest.Status.FAILED);
            request.setFailureMessage(safeFailureMessage(exception));
            requestRepository.save(request);
            throw exception;
        }
    }

    private StreamResult replayExisting(String userId, String idempotencyKey) {
        ChatStreamRequest existing = requestRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Yêu cầu chat đang được xử lý."));
        if (ChatStreamRequest.Status.PENDING.equals(existing.getStatus())) {
            throw new ChatStreamConflictException("Yêu cầu chat đang được xử lý. Vui lòng chờ lịch sử đồng bộ.");
        }
        if (ChatStreamRequest.Status.FAILED.equals(existing.getStatus())) {
            throw new ChatStreamConflictException(
                    existing.getFailureMessage() == null
                            ? "Yêu cầu trước đó chưa hoàn tất. Hãy thử lại bằng một yêu cầu mới."
                            : existing.getFailureMessage()
            );
        }

        ChatMessage userMessage = ownedMessage(existing.getUserMessageId(), userId);
        ChatMessage assistantMessage = ownedMessage(existing.getAssistantMessageId(), userId);
        return new StreamResult(
                userMessage,
                assistantMessage,
                chatService.currentUsage(userId),
                true
        );
    }

    private ChatMessage ownedMessage(String messageId, String userId) {
        ChatMessage message = chatRepository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("Không thể khôi phục kết quả chat."));
        if (!userId.equals(message.getUserId())) {
            throw new IllegalStateException("Không thể khôi phục kết quả chat.");
        }
        return message;
    }

    private String normalizeKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "X-Idempotency-Key phải dài 8-128 ký tự và chỉ gồm chữ, số, '.', '_', ':' hoặc '-'."
            );
        }
        return key;
    }

    private String safeFailureMessage(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException
                || exception instanceof ChatStreamConflictException) {
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return "Hi AI chưa thể trả lời lúc này.";
    }

    public record StreamResult(
            ChatMessage userMessage,
            ChatMessage assistantMessage,
            AiDailyUsageService.Usage aiUsage,
            boolean replayed
    ) {
    }

    public static class ChatStreamConflictException extends RuntimeException {
        public ChatStreamConflictException(String message) {
            super(message);
        }
    }
}
