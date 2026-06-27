package com.hi.api.service;

import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatBoxAIServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void productQuestionUsesUserKeywordsBeforeCallingProvider() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        AffiliateProductService affiliateProductService = mock(AffiliateProductService.class);
        AffiliateProduct product = new AffiliateProduct();
        product.setName("Acne Patch");
        product.setPlatform(AffiliatePlatform.SHOPEE);
        product.setSymptomCategory("mun");
        product.setSymptomTags(List.of("dan mun", "acne"));
        product.setPrice(BigDecimal.valueOf(59000));
        product.setAffiliateUrl("https://shopee.vn/acne-patch");
        when(affiliateProductService.searchProductsByUserKeywords(anyString(), org.mockito.ArgumentMatchers.eq(6)))
                .thenReturn(List.of(product));

        ChatBoxAIService service = new ChatBoxAIService(builderProvider, affiliateProductService);

        String answer = service.chatOnce("ban co san pham dan mun nao goi y minh k", "user-1", "");

        assertThat(answer).contains("HI_PRODUCT|name=Acne+Patch");
        assertThat(answer).contains("price=59000");
        assertThat(answer).contains("shopee.vn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void heatPadProductQuestionUsesKeywordRecommendation() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        AffiliateProductService affiliateProductService = mock(AffiliateProductService.class);
        AffiliateProduct product = new AffiliateProduct();
        product.setName("Tui chuom am");
        product.setPlatform(AffiliatePlatform.SHOPEE);
        product.setSymptomCategory("dau bung kinh");
        product.setSymptomTags(List.of("tui chuom", "dau bung"));
        product.setPrice(BigDecimal.valueOf(99000));
        product.setAffiliateUrl("https://shopee.vn/heat-pad");
        when(affiliateProductService.searchProductsByUserKeywords(anyString(), org.mockito.ArgumentMatchers.eq(6)))
                .thenReturn(List.of(product));

        ChatBoxAIService service = new ChatBoxAIService(builderProvider, affiliateProductService);

        String answer = service.chatOnce("dau bung kinh can tui chuom nao", "user-1", "");

        assertThat(answer).contains("HI_PRODUCT|name=Tui+chuom+am");
        assertThat(answer).contains("price=99000");
        assertThat(answer).contains("shopee.vn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recentCyclesQuestionReturnsRequestedCycleCountWithoutProvider() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String context = """
                Dữ liệu chu kỳ của user:
                - Kỳ gần nhất: 08/06/2026 - --
                - Các kỳ gần đây: [08/06/2026 - --, 10/05/2026 - 14/05/2026, 12/04/2026 - 16/04/2026, 14/03/2026 - 18/03/2026]
                """;

        String answer = service.chatOnce("3 chu ky gan nhat cua toi", "user-1", context);

        assertThat(answer).contains("Mình thấy 3 chu kỳ gần nhất");
        assertThat(answer).contains("Chu kỳ 1: 08/06/2026 - --");
        assertThat(answer).contains("Chu kỳ 2: 10/05/2026 - 14/05/2026");
        assertThat(answer).contains("Chu kỳ 3: 12/04/2026 - 16/04/2026");
        assertThat(answer).doesNotContain("14/03/2026");
        assertThat(answer).doesNotContain("kết nối chậm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerTimeoutFallsBackToRecentCyclesQuickAnswerWhenContextHasCycles() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builderProvider.getIfAvailable()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenThrow(new RuntimeException("timeout"));
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String context = """
                Dữ liệu chu kỳ của user:
                - Kỳ gần nhất: 08/06/2026 - --
                - Các kỳ gần đây: [08/06/2026 - --, 10/05/2026 - 14/05/2026, 12/04/2026 - 16/04/2026]
                """;

        String answer = service.chatOnce("cho minh xem cac chu ky gan nhat", "user-1", context);

        assertThat(answer).contains("Mình thấy 3 chu kỳ gần nhất");
        assertThat(answer).doesNotContain("kết nối chậm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void directHomeCareQuestionProvidesSolutionsBeforeProducts() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String context = """
                Sản phẩm gợi ý đã duyệt để dùng khi thật sự phù hợp. Nếu dùng, chép nguyên dòng HI_PRODUCT tương ứng, không tự viết URL:
                HI_PRODUCT|name=T%C3%BAi+ch%C6%B0%E1%BB%9Dm+%E1%BA%A5m|platform=SHOPEE|tags=%C4%91au+b%E1%BB%A5ng+kinh|price=99000|image=https%3A%2F%2Fexample.com%2Fheat-pad.jpg|url=https%3A%2F%2Fshopee.vn%2Fheat-pad
                """;

        String answer = service.chatOnce("Các phương pháp giảm đau bụng tại nhà", "user-1", context);

        assertThat(answer).contains("Chườm ấm");
        assertThat(answer).contains("Uống nước ấm");
        assertThat(answer).contains("kéo giãn");
        assertThat(answer).contains("mức đau");
        assertThat(answer).contains("HI_PRODUCT|name=");
        assertThat(answer).doesNotContain("affiliate");
        assertThat(answer).contains("bác sĩ");
        assertThat(answer.indexOf("Chườm ấm")).isLessThan(answer.indexOf("HI_PRODUCT|name="));
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaguePainReportTriagesBeforeSuggestingProducts() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String context = """
                Sản phẩm gợi ý đã duyệt để dùng khi thật sự phù hợp:
                HI_PRODUCT|name=Tui+chuom|platform=SHOPEE|url=https%3A%2F%2Fshopee.vn%2Fheat-pad
                """;

        String answer = service.chatOnce("Mình đau bụng quá", "user-1", context);

        assertThat(answer).contains("mức đau");
        assertThat(answer).contains("Chườm ấm");
        assertThat(answer).doesNotContain("HI_PRODUCT|");
    }

    @Test
    @SuppressWarnings("unchecked")
    void directHomeCareQuestionStillWorksWithoutApprovedProducts() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable()).thenReturn(null);
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String answer = service.chatOnce("Làm sao giảm đau bụng kinh tại nhà?", "user-1", "");

        assertThat(answer).contains("Chườm ấm");
        assertThat(answer).contains("kéo giãn");
        assertThat(answer).doesNotContain("chưa có sản phẩm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerTimeoutUsesTemporaryMessageInsteadOfConfigurationMessage() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builderProvider.getIfAvailable()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenThrow(new RuntimeException("timeout"));
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String answer = service.chatOnce("Tôi nên chăm sóc sức khỏe thế nào?", "user-1", "");

        assertThat(answer).contains("kết nối chậm hơn bình thường");
        assertThat(answer).doesNotContain("cần cấu hình nhà cung cấp AI");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyTipAcceptsConciseCareMessage() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builderProvider.getIfAvailable()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn("Hi nhắc bạn uống đủ nước, vận động nhẹ và dành vài phút nghỉ ngơi hôm nay nhé.");
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String answer = service.generateDailyTip("user-1", "Dữ liệu sức khỏe riêng tư");

        assertThat(answer).contains("uống đủ nước");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyTipRejectsRawHealthContextDump() {
        ObjectProvider<ChatClient.Builder> builderProvider = mock(ObjectProvider.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builderProvider.getIfAvailable()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn("Triệu chứng trong kỳ gần nhất: Mood score: 4, kỳ tiếp theo ước tính: 08/07/2026.");
        ChatBoxAIService service = new ChatBoxAIService(builderProvider);

        String answer = service.generateDailyTip("user-1", "Dữ liệu sức khỏe riêng tư");

        assertThat(answer).isEmpty();
    }
}
