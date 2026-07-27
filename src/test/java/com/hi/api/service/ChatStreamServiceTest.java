package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.model.ChatStreamRequest;
import com.hi.api.repository.ChatRepository;
import com.hi.api.repository.ChatStreamRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ChatStreamServiceTest {

    @Test
    void firstRequestPersistsCompletedResult() {
        ChatStreamRequestRepository requestRepository = mock(ChatStreamRequestRepository.class);
        ChatRepository chatRepository = mock(ChatRepository.class);
        ChatService chatService = mock(ChatService.class);
        ChatStreamService service = new ChatStreamService(requestRepository, chatRepository, chatService);
        ChatMessage userMessage = message("user-message", "user", "Xin chào");
        ChatMessage assistantMessage = message("assistant-message", "assistant", "Chào bạn");
        AiDailyUsageService.Usage usage = new AiDailyUsageService.Usage(10, 1, 9, Instant.now());

        when(requestRepository.save(any(ChatStreamRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatService.sendMessage("user-1", "Xin chào", LocalDate.of(2026, 7, 25)))
                .thenReturn(new ChatService.SendResult(userMessage, assistantMessage, usage));

        ChatStreamService.StreamResult result = service.execute(
                "user-1",
                "mobile-12345678",
                "Xin chào",
                LocalDate.of(2026, 7, 25)
        );

        assertFalse(result.replayed());
        assertEquals("assistant-message", result.assistantMessage().getId());
        verify(requestRepository, times(2)).save(any(ChatStreamRequest.class));
    }

    @Test
    void completedDuplicateReplaysSavedMessagesWithoutCallingAiAgain() {
        ChatStreamRequestRepository requestRepository = mock(ChatStreamRequestRepository.class);
        ChatRepository chatRepository = mock(ChatRepository.class);
        ChatService chatService = mock(ChatService.class);
        ChatStreamService service = new ChatStreamService(requestRepository, chatRepository, chatService);
        ChatStreamRequest existing = new ChatStreamRequest();
        existing.setUserId("user-1");
        existing.setIdempotencyKey("mobile-12345678");
        existing.setStatus(ChatStreamRequest.Status.COMPLETED);
        existing.setUserMessageId("u1");
        existing.setAssistantMessageId("a1");
        ChatMessage userMessage = message("u1", "user", "Xin chào");
        ChatMessage assistantMessage = message("a1", "assistant", "Chào bạn");
        AiDailyUsageService.Usage usage = new AiDailyUsageService.Usage(10, 1, 9, Instant.now());

        when(requestRepository.save(any(ChatStreamRequest.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(requestRepository.findByUserIdAndIdempotencyKey("user-1", "mobile-12345678"))
                .thenReturn(Optional.of(existing));
        when(chatRepository.findById("u1")).thenReturn(Optional.of(userMessage));
        when(chatRepository.findById("a1")).thenReturn(Optional.of(assistantMessage));
        when(chatService.currentUsage("user-1")).thenReturn(usage);

        ChatStreamService.StreamResult result = service.execute(
                "user-1",
                "mobile-12345678",
                "Xin chào",
                LocalDate.of(2026, 7, 25)
        );

        assertTrue(result.replayed());
        assertEquals("Chào bạn", result.assistantMessage().getContent());
    }

    @Test
    void rejectsUnsafeIdempotencyKey() {
        ChatStreamService service = new ChatStreamService(
                mock(ChatStreamRequestRepository.class),
                mock(ChatRepository.class),
                mock(ChatService.class)
        );
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                "user-1",
                "bad key",
                "Xin chào",
                LocalDate.now()
        ));
    }

    private ChatMessage message(String id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setUserId("user-1");
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
