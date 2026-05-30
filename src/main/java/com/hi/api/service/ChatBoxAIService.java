package com.hi.api.service;

import com.hi.api.repository.ChatBoxAIRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Service
public class ChatBoxAIService {
    private final ChatClient chatClient;
    private final ChatBoxAIRepository chatBoxAIRepository;
    @Autowired
    private StreamingChatModel streamingChatModel;



    public ChatBoxAIService(ChatClient.Builder builder, ChatBoxAIRepository chatBoxAIRepository) {
        this.chatBoxAIRepository = chatBoxAIRepository;
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatBoxAIRepository)
                .maxMessages(35)
                .build();
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public Flux<String> chatStream(String userInput, String id) {
        SystemMessage systemMessage = new SystemMessage("""
                       "Bạn là trợ lý sức khỏe sinh sản thân thiện của ứng dụng Hi.
                       "Bạn chuyên tư vấn về chu kỳ kinh nguyệt, sức khỏe sinh sản cho cả nam và nữ.
                       "Trả lời bằng tiếng Việt, ngắn gọn, cảm thông và dựa trên kiến thức y khoa.
                       "Nếu câu hỏi nghiêm trọng, hãy khuyên người dùng gặp bác sĩ.
                """);
        UserMessage userMessage = new UserMessage(userInput);
//        Message[] messages = new Message[]{systemMessage, userMessage};
//        Prompt prompt = Prompt.builder().messages(messages).build();
//        return streamingChatModel.stream(prompt).flatMap(response -> Flux.just(response.getResult().getOutput().getText()));
        return chatClient.prompt()
                .system(systemMessage.getText())
                .user(userMessage.getText())
                .advisors(a ->
                        a.param(ChatMemory.CONVERSATION_ID, 2))
                .stream()
                .content();


    }

}
