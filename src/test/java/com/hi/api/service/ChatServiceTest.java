package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @Test
    void recentConversationIsIncludedWhenGeneratingNextAnswer() {
        ChatRepository chatRepository = mock(ChatRepository.class);
        ChatBoxAIService aiService = mock(ChatBoxAIService.class);
        ChatContextService contextService = mock(ChatContextService.class);
        SubscriptionAccessService subscriptionAccessService = mock(SubscriptionAccessService.class);
        AiDailyUsageService aiDailyUsageService = mock(AiDailyUsageService.class);
        AiRequestAdmissionService aiRequestAdmissionService = mock(AiRequestAdmissionService.class);
        ChatService service = new ChatService(
                chatRepository,
                aiService,
                contextService,
                subscriptionAccessService,
                aiDailyUsageService,
                aiRequestAdmissionService
        );
        LocalDate sessionDate = LocalDate.of(2026, 6, 12);
        when(subscriptionAccessService.getAccess("user-1")).thenReturn(
                new SubscriptionAccessService.SubscriptionAccess(
                        "FREE",
                        "FREE",
                        false,
                        null,
                        false,
                        5,
                        java.util.Map.of()
                )
        );
        when(aiDailyUsageService.reserve("user-1", 5))
                .thenReturn(new AiDailyUsageService.Usage(
                        5,
                        1,
                        4,
                        Instant.parse("2026-06-12T17:00:00Z")
                ));
        when(aiRequestAdmissionService.execute(eq(false), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(1);
                    return action.get();
                });

        ChatMessage previousUser = message("previous-user", "user", "Mình đau bụng kỳ kinh", sessionDate, 1);
        ChatMessage previousAssistant = message(
                "previous-assistant",
                "assistant",
                "Bạn đau mức mấy trên 10 và có sốt không?",
                sessionDate,
                2
        );

        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("assistant".equals(saved.getRole()) ? "new-assistant" : "current-user");
            }
            return saved;
        });
        when(chatRepository.findByUserIdAndSessionDateOrderByCreatedAtAsc("user-1", sessionDate))
                .thenReturn(List.of(previousUser, previousAssistant));
        when(chatRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq("user-1"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(contextService.buildContext("user-1")).thenReturn("Dữ liệu sức khỏe hiện tại");
        when(aiService.chatOnce(eq("Đau 7/10, không sốt"), eq("user-1"), any()))
                .thenReturn("Câu trả lời đã cá nhân hóa");

        service.sendMessage("user-1", "Đau 7/10, không sốt", sessionDate);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).chatOnce(eq("Đau 7/10, không sốt"), eq("user-1"), contextCaptor.capture());
        assertThat(contextCaptor.getValue())
                .contains("Hội thoại gần đây trong phiên này")
                .contains("User: Mình đau bụng kỳ kinh")
                .contains("Hi AI: Bạn đau mức mấy trên 10 và có sốt không?");
    }

    private ChatMessage message(String id, String role, String content, LocalDate sessionDate, long seconds) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setUserId("user-1");
        message.setRole(role);
        message.setContent(content);
        message.setSessionDate(sessionDate);
        message.setCreatedAt(Instant.parse("2026-06-11T19:00:00Z").plusSeconds(seconds));
        return message;
    }
}
