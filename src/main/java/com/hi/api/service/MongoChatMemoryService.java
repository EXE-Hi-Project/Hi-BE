package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import com.hi.api.repository.ChatRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MongoChatMemoryService {
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    private final ChatRepository chatRepository;

    public MongoChatMemoryService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public void saveMessage(User user, String request, String response) {
        if (user == null || user.getId() == null) {
            return;
        }

        List<ChatMessage> messages = new ArrayList<>(2);
        if (request != null && !request.isBlank()) {
            messages.add(new ChatMessage(user.getId(), "user", request));
        }
        if (response != null && !response.isBlank()) {
            messages.add(new ChatMessage(user.getId(), "assistant", response));
        }

        if (!messages.isEmpty()) {
            chatRepository.saveAll(messages);
        }
    }

    public ChatMessage getChatHistory(User user) {
        List<ChatMessage> history = getChatHistory(user, DEFAULT_HISTORY_LIMIT);
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public List<ChatMessage> getChatHistory(User user, int limit) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ChatMessage> history = new ArrayList<>(chatRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(),
                PageRequest.of(0, safeLimit)
        ));
        Collections.reverse(history);
        return history;
    }


}
