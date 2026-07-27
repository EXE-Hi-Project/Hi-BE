package com.hi.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerChunkTest {

    @Test
    void chunksPreserveValidatedAnswerExactly() {
        String answer = "Đây là câu trả lời đã được kiểm tra trước khi phát theo từng đoạn.";
        var chunks = ChatController.chunks(answer, 18);
        assertTrue(chunks.size() > 1);
        assertEquals(answer, String.join("", chunks));
    }
}
