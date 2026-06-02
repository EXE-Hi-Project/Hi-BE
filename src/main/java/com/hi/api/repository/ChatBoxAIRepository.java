package com.hi.api.repository;

import com.hi.api.model.ChatMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatBoxAIRepository implements ChatMemoryRepository {

    private static final int MAX_MEMORY_MESSAGES = 35;

    private final SpringDataMongoChatRepository chatRepository;

    public ChatBoxAIRepository(SpringDataMongoChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Override
    public List<String> findConversationIds() {
        return chatRepository.findDistinctUserIdBy();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return chatRepository.findByUserIdOrderByCreatedAtAsc(
                        conversationId,
                        PageRequest.of(0, MAX_MEMORY_MESSAGES)
                )
                .stream()
                .map(this::toSpringAiMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<ChatMessage> entities = messages.stream()
                .map(message -> toChatMessage(conversationId, message))
                .toList();
        chatRepository.saveAll(entities);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatRepository.deleteByUserId(conversationId);
    }

    private Message toSpringAiMessage(ChatMessage chatMessage) {
        String role = chatMessage.getRole();
        if ("assistant".equalsIgnoreCase(role)) {
            return new AssistantMessage(chatMessage.getContent());
        }
        if ("system".equalsIgnoreCase(role)) {
            return new SystemMessage(chatMessage.getContent());
        }
        return new UserMessage(chatMessage.getContent());
    }

    private ChatMessage toChatMessage(String conversationId, Message message) {
        ChatMessage entity = new ChatMessage();
        entity.setUserId(conversationId);
        entity.setRole(message.getMessageType().getValue());
        entity.setContent(message.getText());
        return entity;
    }
}
