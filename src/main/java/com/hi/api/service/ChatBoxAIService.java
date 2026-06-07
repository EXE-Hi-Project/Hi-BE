package com.hi.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class ChatBoxAIService {

    private static final Logger log = LoggerFactory.getLogger(ChatBoxAIService.class);

    private final ChatClient chatClient;

    public ChatBoxAIService(ObjectProvider<ChatClient.Builder> builderProvider) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder != null ? builder.build() : null;
    }

    public String chatOnce(String userInput, String userId, String userContext) {
        if (chatClient == null) {
            return fallbackAnswer(userInput, userContext);
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt(userContext))
                    .user(userInput)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("AI chat failed for user {}: {}", userId, ex.getMessage());
            return fallbackAnswer(userInput, userContext);
        }
    }

    public Flux<String> chatStream(String userInput, String userId, String userContext) {
        if (chatClient == null) {
            return Flux.just(fallbackAnswer(userInput, userContext));
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt(userContext))
                    .user(userInput)
                    .stream()
                    .content()
                    .onErrorResume(ex -> {
                        log.warn("AI stream failed for user {}: {}", userId, ex.getMessage());
                        return Flux.just(fallbackAnswer(userInput, userContext));
                    });
        } catch (Exception ex) {
            log.warn("AI stream setup failed for user {}: {}", userId, ex.getMessage());
            return Flux.just(fallbackAnswer(userInput, userContext));
        }
    }

    private String systemPrompt(String userContext) {
        return """
                Bạn là Hi AI, trợ lý sức khỏe sinh sản thân thiện của ứng dụng Hi.

                Tính cách:
                - Gần gũi, ấm áp, hơi nhí nhảnh nhẹ như một người bạn biết quan tâm.
                - Có thể thêm một câu vui nhỏ, nhưng không dùng quá nhiều emoji.
                - Gọi người dùng là "bạn", không phán xét, không làm họ lo lắng quá mức.

                Cách trả lời:
                - Trả lời bằng tiếng Việt rõ ràng.
                - Chia thành đoạn ngắn hoặc bullet list, không viết thành một khối dài.
                - Khi nói về chu kỳ, rụng trứng hoặc cửa sổ thụ thai, luôn ghi đây là ước tính.
                - Không chẩn đoán bệnh, không thay thế bác sĩ, không thay thế biện pháp tránh thai.
                - Nếu có triệu chứng nghiêm trọng, khuyên người dùng liên hệ bác sĩ hoặc cơ sở y tế.
                - Chỉ dùng dữ liệu trong ngữ cảnh cho user hiện tại và Người ấy đã kết nối.
                - Nếu gợi ý sản phẩm affiliate, nói rõ: "Đây là link affiliate, Hi có thể nhận hoa hồng nếu bạn mua qua link này."
                - Ưu tiên sản phẩm hỗ trợ tại nhà như túi chườm, miếng dán ấm, trà gừng; không gợi ý thuốc nếu không có dữ liệu kiểm duyệt.

                Ngữ cảnh:
                %s
                """.formatted(userContext == null ? "" : userContext);
    }

    private String fallbackAnswer(String userInput, String userContext) {
        String normalized = normalize(userInput);

        if (containsAny(normalized, "tinh nang", "hi la gi", "goi hi", "cac goi", "premium", "free", "gia goi")) {
            return """
                    Hi có thể giúp bạn theo dõi sức khỏe sinh sản theo cách nhẹ nhàng và cá nhân hóa hơn nè:

                    - Theo dõi chu kỳ, lịch sử kỳ kinh và kỳ tiếp theo ước tính.
                    - Ghi triệu chứng, cảm xúc, lượng kinh và ghi chú hằng ngày.
                    - Kết nối Người ấy để chia sẻ những thông tin bạn cho phép.
                    - Nhận thông báo web, email nhắc gần tới kỳ và hỏi thăm hằng ngày.
                    - Xem video sức khỏe được duyệt và trò chuyện với Hi AI.
                    - Gợi ý sản phẩm hỗ trợ tại nhà qua affiliate khi phù hợp.

                    Các gói Hi:
                    - Free: theo dõi cơ bản, lịch sử cá nhân, nhắc cơ bản và AI giới hạn.
                    - Premium tháng: analytics nâng cao, AI Premium và chia sẻ Người ấy nâng cao.
                    - Premium năm: toàn bộ Premium tháng, báo cáo định kỳ và ưu đãi tiết kiệm.
                    """;
        }

        if (containsAny(normalized, "chu ky truoc", "ky truoc", "lan truoc", "ky gan nhat")) {
            String cycleLine = extractLine(userContext, "- Kỳ gần nhất:");
            if (!cycleLine.isBlank() && !cycleLine.contains("Chưa có")) {
                return """
                        Mình thấy kỳ gần nhất đã ghi trong Hi là:

                        - %s

                        Đây là kỳ đã được xác nhận trong lịch sử, khác với các ngày dự đoán nha.
                        """.formatted(cleanLabel(cycleLine));
            }
            return """
                    Mình chưa thấy kỳ đã xác nhận trong dữ liệu hiện tại.

                    Bạn có thể vào phần "Thêm lịch sử" hoặc "Bắt đầu kỳ hôm nay" để ghi nhận kỳ thật. Khi có dữ liệu rồi, Hi sẽ trả lời chính xác hơn nhiều đó.
                    """;
        }

        if (containsAny(normalized, "ky tiep theo", "sap toi ky", "toi ky", "du kien khi nao")) {
            String nextPeriod = extractLine(userContext, "- Kỳ tiếp theo ước tính:");
            String ovulation = extractLine(userContext, "- Rụng trứng ước tính:");
            if (!nextPeriod.isBlank()) {
                return """
                        Theo dữ liệu hiện tại trong Hi:

                        - %s
                        - %s

                        Các mốc này là ước tính, không dùng thay thế biện pháp tránh thai hoặc tư vấn y khoa nhé.
                        """.formatted(cleanLabel(nextPeriod), cleanLabel(ovulation));
            }
        }

        if (containsAny(normalized, "dau bung", "dau bung kinh", "chuot rut", "toi dau")) {
            String products = affiliateLines(userContext);
            return """
                    Ôi, đau bụng kỳ kinh đúng là mệt thật. Mình ở đây với bạn nè.

                    Bạn có thể thử vài cách dịu nhẹ trước:
                    - Chườm ấm vùng bụng dưới 15-20 phút.
                    - Uống nước ấm, nghỉ một chút và tránh vận động quá mạnh.
                    - Ghi lại mức đau trong Hi để mình theo dõi xu hướng cho bạn.

                    %s

                    Nếu đau dữ dội, kéo dài bất thường, chóng mặt hoặc ra máu quá nhiều, bạn nên liên hệ bác sĩ/cơ sở y tế nhé.
                    """.formatted(products);
        }

        if (containsAny(normalized, "trieu chung", "lich su trieu chung", "trieu chung trong ky")) {
            String symptomBlock = extractBlock(userContext, "- Triệu chứng trong kỳ gần nhất:");
            if (!symptomBlock.isBlank()) {
                return """
                        Mình đọc được phần triệu chứng trong kỳ gần nhất như sau:

                        %s

                        Đây là dữ liệu bạn đã ghi trong Hi, không phải chẩn đoán y khoa nha.
                        """.formatted(symptomBlock);
            }
            return """
                    Hi có thể giúp bạn xem triệu chứng theo từng ngày trong kỳ:

                    - Ngày nào có lượng kinh ít/vừa/nhiều.
                    - Các triệu chứng cơ thể và tâm trạng đã ghi.
                    - Ghi chú cá nhân trong từng ngày.

                    Hiện mình chưa thấy log triệu chứng trong kỳ gần nhất. Bạn có thể vào Lịch sử chu kỳ để ghi/cập nhật từng ngày trong kỳ nhé.
                    """;
        }

        if (containsAny(normalized, "thong bao", "email", "nhac", "sms")) {
            return """
                    Hi hiện hỗ trợ:

                    - Push App: thông báo trên web/app.
                    - Email: gửi khi cấu hình mail hoạt động và bạn bật kênh email.
                    - SMS: đang để "sắp hỗ trợ" trong MVP.

                    Các nhóm nhắc gồm: gần tới kỳ, cửa sổ thụ thai/rụng trứng ước tính, lời khuyên hằng ngày, cập nhật cảm xúc và gợi ý chăm sóc cho Người ấy.
                    """;
        }

        return fallbackUnavailable();
    }

    private String affiliateLines(String context) {
        String lines = extractBlock(context, "Sản phẩm affiliate đã duyệt");
        if (lines.isBlank()) {
            return "Hi chưa có sản phẩm hỗ trợ đã duyệt để gợi ý lúc này.";
        }
        return "Một vài sản phẩm hỗ trợ tại nhà có thể phù hợp:\n\n" +
                lines.lines()
                        .filter(line -> line.trim().startsWith("-"))
                        .limit(3)
                        .map(line -> line + "\n  Đây là link affiliate, Hi có thể nhận hoa hồng nếu bạn mua qua link này.")
                        .reduce("", (a, b) -> a + b + "\n");
    }

    private String fallbackUnavailable() {
        return """
                Hi AI đang cần cấu hình nhà cung cấp AI để trả lời sâu hơn.

                Mình vẫn có thể hỗ trợ các câu hỏi cơ bản về Hi, gói Free/Premium, thông báo, video, affiliate và dữ liệu chu kỳ đã lưu.

                Nếu có triệu chứng nghiêm trọng, hãy liên hệ bác sĩ hoặc cơ sở y tế nhé.
                """;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) return "";
        String noAccent = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccent.toLowerCase(Locale.ROOT);
    }

    private String extractLine(String text, String marker) {
        if (text == null || !text.contains(marker)) return "";
        int start = text.indexOf(marker);
        int end = text.indexOf("\n", start);
        return end > start ? text.substring(start, end).trim() : text.substring(start).trim();
    }

    private String extractBlock(String text, String marker) {
        if (text == null || !text.contains(marker)) return "";
        int start = text.indexOf(marker);
        int nextSection = text.indexOf("\n\n", start + marker.length());
        return nextSection > start ? text.substring(start, nextSection).trim() : text.substring(start).trim();
    }

    private String cleanLabel(String value) {
        return value.replaceFirst("^-\\s*", "").trim();
    }
}
