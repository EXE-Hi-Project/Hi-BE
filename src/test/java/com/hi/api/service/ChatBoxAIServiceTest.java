package com.hi.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatBoxAIServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void fallbackSuggestsAffiliateProductsWhenUserMentionsAbdominalPain() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String context = """
                Sản phẩm affiliate đã duyệt để gợi ý nhẹ nhàng khi phù hợp:
                - Túi chườm ấm | nền tảng: SHOPEE | nhóm: chăm sóc tại nhà | tags: đau bụng kinh | link: https://shopee.vn/heat-pad
                """;

        String answer = service.chatOnce("Mình đau bụng quá", "user-1", context);

        assertThat(answer).contains("mức đau");
        assertThat(answer).contains("Túi chườm ấm");
        assertThat(answer).contains("Đây là link affiliate");
        assertThat(answer).contains("bác sĩ");
    }
}
