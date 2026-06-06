package com.hi.api.service;

import com.hi.api.model.ChatMessage;
import com.hi.api.model.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatBoxAIService {
    @Autowired
    private MongoChatMemoryService mongoChatMemoryService;

    private final ChatClient chatClient;


    SystemMessage systemMessage = new SystemMessage("""
            Bạn là trợ lý sức khỏe sinh sản thân thiện của ứng dụng Hi.
            Bạn chuyên tư vấn về chu kỳ kinh nguyệt, sức khỏe sinh sản cho cả nam và nữ.
            Trả lời bằng tiếng Việt, ngắn gọn, cảm thông và dựa trên kiến thức y khoa.
            Nếu câu hỏi nghiêm trọng, hãy khuyên người dùng gặp bác sĩ.
            """);

    public ChatBoxAIService(ChatClient.Builder builder ){
        this.chatClient = builder.build();
    }

    public Flux<String> chatStream(String userInput, User user) {
        String safeUserInput = userInput == null ? "" : userInput;
        StringBuilder fullResponse = new StringBuilder();
        String safeSystemPrompt = systemMessage.getText() == null ? "" : systemMessage.getText();
        return chatClient.prompt()
                .system(safeSystemPrompt)
                .user(safeUserInput)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> persistHistory(user, safeUserInput, fullResponse.toString()));

    }

    private void persistHistory(User user, String request, String response) {
        if (user != null) {
            mongoChatMemoryService.saveMessage(user, request, response);
        }
    }


    public List<ChatMessage> getHistory(User user, int limit) {
        return mongoChatMemoryService.getChatHistory(user, limit);
    }



}