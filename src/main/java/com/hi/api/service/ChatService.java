package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.ChatMessage;
import com.hi.api.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

//    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
//
//    private static final String SYSTEM_PROMPT =
//            "Bạn là trợ lý sức khỏe sinh sản thân thiện của ứng dụng Hi. " +
//            "Bạn chuyên tư vấn về chu kỳ kinh nguyệt, sức khỏe sinh sản cho cả nam và nữ. " +
//            "Trả lời bằng tiếng Việt, ngắn gọn, cảm thông và dựa trên kiến thức y khoa. " +
//            "Nếu câu hỏi nghiêm trọng, hãy khuyên người dùng gặp bác sĩ.";
//
//    private final ChatRepository chatRepository;
//    private final BedrockRuntimeClient bedrockClient;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Value("${app.bedrock.model-id:anthropic.claude-3-haiku-20240307-v1:0}")
//    private String modelId;
//
//    public ChatService(ChatRepository chatRepository, BedrockRuntimeClient bedrockClient) {
//        this.chatRepository = chatRepository;
//        this.bedrockClient = bedrockClient;
//    }
//
//    public List<ChatMessage> getHistory(String userId) {
//        return chatRepository.findByUserIdOrderByCreatedAtAsc(
//                userId, PageRequest.of(0, 100));
//    }
//
//    public ChatMessage sendMessage(String userId, String content) throws Exception {
//        // Save user message
//        ChatMessage userMsg = new ChatMessage();
//        userMsg.setUserId(userId);
//        userMsg.setRole("user");
//        userMsg.setContent(content);
//        chatRepository.save(userMsg);
//
//        // Fetch recent history for context (last 10 messages, descending, then reverse)
//        List<ChatMessage> recent = chatRepository.findByUserIdOrderByCreatedAtDesc(
//                userId, PageRequest.of(0, 10));
//        List<Map<String, String>> messages = new ArrayList<>();
//        for (int i = recent.size() - 1; i >= 0; i--) {
//            ChatMessage m = recent.get(i);
//            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
//        }
//
//        // Build Bedrock request body
//        Map<String, Object> body = Map.of(
//                "anthropic_version", "bedrock-2023-05-31",
//                "max_tokens", 512,
//                "system", SYSTEM_PROMPT,
//                "messages", messages
//        );
//
//        String bodyJson = objectMapper.writeValueAsString(body);
//
//        InvokeModelRequest request = InvokeModelRequest.builder()
//                .modelId(modelId)
//                .contentType("application/json")
//                .accept("application/json")
//                .body(SdkBytes.fromUtf8String(bodyJson))
//                .build();
//
//        InvokeModelResponse response = bedrockClient.invokeModel(request);
//        String responseBody = response.body().asUtf8String();
//        JsonNode root = objectMapper.readTree(responseBody);
//        String aiContent = root.path("content").get(0).path("text").asText();
//
//        // Save AI message
//        ChatMessage aiMsg = new ChatMessage();
//        aiMsg.setUserId(userId);
//        aiMsg.setRole("assistant");
//        aiMsg.setContent(aiContent);
//        chatRepository.save(aiMsg);
//
//        return aiMsg;
//    }
}
