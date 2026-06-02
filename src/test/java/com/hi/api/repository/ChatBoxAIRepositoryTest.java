package com.hi.api.repository;

import com.hi.api.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatBoxAIRepositoryTest {

    private SpringDataMongoChatRepository mongoRepository;
    private ChatBoxAIRepository repository;

    @BeforeEach
    void setUp() {
        mongoRepository = mock(SpringDataMongoChatRepository.class);
        repository = new ChatBoxAIRepository(mongoRepository);
    }

    @Test
    void findByConversationIdMapsStoredMessagesToSpringAiMessages() {
        ChatMessage system = chatMessage("user-1", "system", "system prompt");
        ChatMessage user = chatMessage("user-1", "user", "hello");
        ChatMessage assistant = chatMessage("user-1", "assistant", "xin chao");

        when(mongoRepository.findByUserIdOrderByCreatedAtAsc(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(system, user, assistant));

        List<Message> messages = repository.findByConversationId("user-1");

        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("xin chao", messages.get(2).getText());
    }

    @Test
    void saveAllPersistsConversationIdAndRole() {
        repository.saveAll("user-2", List.of(
                new SystemMessage("rules"),
                new UserMessage("question"),
                new AssistantMessage("answer")
        ));

        verify(mongoRepository).saveAll(List.of(
                chatMessage("user-2", "system", "rules"),
                chatMessage("user-2", "user", "question"),
                chatMessage("user-2", "assistant", "answer")
        ));
    }

    @Test
    void deleteByConversationIdDelegatesToMongoRepository() {
        repository.deleteByConversationId("user-3");

        verify(mongoRepository).deleteByUserId("user-3");
    }

    private ChatMessage chatMessage(String userId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
