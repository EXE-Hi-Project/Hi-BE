package com.hi.api.service;

import com.hi.api.model.AffiliateProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatBoxAIService {

    private static final Logger log = LoggerFactory.getLogger(ChatBoxAIService.class);

    private final ChatClient chatClient;
    private final AffiliateProductService affiliateProductService;

    public ChatBoxAIService(ObjectProvider<ChatClient.Builder> builderProvider) {
        this(builderProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChatBoxAIService(ObjectProvider<ChatClient.Builder> builderProvider,
                            AffiliateProductService affiliateProductService) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder != null ? builder.build() : null;
        this.affiliateProductService = affiliateProductService;
    }

    public String chatOnce(String userInput, String userId, String userContext) {
        String quickAnswer = quickAnswer(userInput, userContext);
        if (!quickAnswer.isBlank()) {
            return quickAnswer;
        }
        if (chatClient == null) {
            return fallbackAnswer(userInput, userContext);
        }
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt(userContext))
                    .user(userInput)
                    .call()
                    .content();
            return enforceAnswerPolicy(userInput, answer);
        } catch (Exception ex) {
            log.warn("AI chat failed for user {}: {}", userId, ex.getMessage());
            return temporaryFailureAnswer(userInput, userContext);
        }
    }

    public Flux<String> chatStream(String userInput, String userId, String userContext) {
        String quickAnswer = quickAnswer(userInput, userContext);
        if (!quickAnswer.isBlank()) {
            return Flux.just(quickAnswer);
        }
        if (chatClient == null) {
            return Flux.just(fallbackAnswer(userInput, userContext));
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt(userContext))
                    .user(userInput)
                    .stream()
                    .content()
                    .collectList()
                    .map(parts -> enforceAnswerPolicy(userInput, String.join("", parts)))
                    .flux()
                    .onErrorResume(ex -> {
                        log.warn("AI stream failed for user {}: {}", userId, ex.getMessage());
                        return Flux.just(temporaryFailureAnswer(userInput, userContext));
                    });
        } catch (Exception ex) {
            log.warn("AI stream setup failed for user {}: {}", userId, ex.getMessage());
            return Flux.just(temporaryFailureAnswer(userInput, userContext));
        }
    }

    public String generateDailyTip(String userId, String userContext) {
        if (chatClient == null) {
            return "";
        }
        String prompt = """
                Tạo một lời hỏi thăm sức khỏe bằng tiếng Việt cho hôm nay.
                Yêu cầu bắt buộc:
                - Xưng là "Hi", gọi người dùng là "bạn".
                - Tối đa 80 từ, chỉ 1-2 đoạn ngắn.
                - Chỉ đưa một gợi ý chăm sóc nhẹ nhàng, thực tế.
                - Không chép dữ liệu đầu vào, tên trường, mã trạng thái, điểm số, ngày tháng hoặc danh sách lịch sử.
                - Không chẩn đoán và không giới thiệu sản phẩm.
                """;
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt(userContext))
                    .user(prompt)
                    .call()
                    .content();
            return sanitizeDailyTip(answer);
        } catch (Exception ex) {
            log.warn("Daily AI tip failed for user {}: {}", userId, ex.getMessage());
            return "";
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
                - Ưu tiên trả lời ngắn, có cấu trúc và đi thẳng vào nhu cầu chính.
                - Chia thành nhiều đoạn ngắn hoặc bullet list, không viết thành một khối dài.
                - Nếu user hỏi trực tiếp "cách", "phương pháp", "nên làm gì" hoặc "xử lý tại nhà", phải cung cấp các bước an toàn, thực tế ngay trong câu trả lời hiện tại. Không được chỉ hỏi thêm rồi chuyển sang sản phẩm.
                - Nếu triệu chứng còn mơ hồ, vẫn đưa 2-3 bước chăm sóc ban đầu an toàn, sau đó hỏi tối đa 3 câu ngắn để cá nhân hóa lượt tiếp theo.
                - Với câu trả lời chăm sóc triệu chứng, thứ tự bắt buộc là: giải pháp không dùng sản phẩm -> câu hỏi cá nhân hóa nếu cần -> dấu hiệu cần khám -> sản phẩm hỗ trợ tùy chọn.
                - Khi nói về chu kỳ, rụng trứng hoặc cửa sổ thụ thai, luôn ghi đây là ước tính.
                - Không chẩn đoán bệnh, không thay thế bác sĩ, không thay thế biện pháp tránh thai.
                - Nếu có triệu chứng nghiêm trọng, khuyên người dùng liên hệ bác sĩ hoặc cơ sở y tế.
                - Chỉ dùng dữ liệu trong ngữ cảnh cho user hiện tại và Người ấy đã kết nối.
                - Sản phẩm chỉ là phần bổ sung tùy chọn, không phải giải pháp chính. Chỉ gợi ý sau khi đã có ít nhất 3 hành động chăm sóc cụ thể và phù hợp.
                - Không giới thiệu sản phẩm trong lượt sàng lọc đầu tiên nếu user chỉ báo một triệu chứng mơ hồ.
                - Nếu gợi ý sản phẩm, đặt ở cuối câu trả lời, chỉ dùng tối đa 2 dòng bắt đầu bằng HI_PRODUCT có trong ngữ cảnh. Chép nguyên dòng đó, không viết URL trực tiếp và không nhắc affiliate/hoa hồng với user.
                - Không thông báo "chưa có sản phẩm đã duyệt" trừ khi user chủ động hỏi về sản phẩm.
                - Không gợi ý thuốc nếu không có dữ liệu kiểm duyệt.

                Ngữ cảnh:
                %s
                """.formatted(userContext == null ? "" : userContext);
    }

    private String fallbackAnswer(String userInput, String userContext) {
        String answer = quickAnswer(userInput, userContext);
        return answer.isBlank() ? fallbackUnavailable() : answer;
    }

    private String temporaryFailureAnswer(String userInput, String userContext) {
        String answer = quickAnswer(userInput, userContext);
        return answer.isBlank() ? fallbackTemporaryUnavailable() : answer;
    }

    private String quickAnswer(String userInput, String userContext) {
        String normalized = normalize(userInput);

        String productAnswer = productQuickAnswer(userInput, normalized);
        if (!productAnswer.isBlank()) {
            return productAnswer;
        }

        String recentCyclesAnswer = recentCyclesAnswer(userInput, normalized, userContext);
        if (!recentCyclesAnswer.isBlank()) {
            return recentCyclesAnswer;
        }

        if (containsAny(normalized, "tinh nang", "hi la gi", "goi hi", "cac goi", "premium", "free", "gia goi")) {
            return """
                    Hi có thể giúp bạn theo dõi sức khỏe sinh sản theo cách nhẹ nhàng và cá nhân hóa hơn nè:

                    - Theo dõi chu kỳ, lịch sử kỳ kinh và kỳ tiếp theo ước tính.
                    - Ghi triệu chứng, cảm xúc, lượng kinh và ghi chú hằng ngày.
                    - Kết nối Người ấy để chia sẻ những thông tin bạn cho phép.
                    - Nhận thông báo web, email nhắc gần tới kỳ và hỏi thăm hằng ngày.
                    - Xem video sức khỏe được duyệt, trò chuyện với Hi AI và xem gợi ý sản phẩm hỗ trợ khi phù hợp.

                    Các gói Hi:
                    - Free: đầy đủ theo dõi và lịch sử sức khỏe, dự đoán cơ bản, cảnh báo an toàn, mọi phong cách AI, email và lịch nhắc tùy chỉnh; tối đa 5 câu trả lời AI mỗi ngày.
                    - Premium tháng: toàn bộ Free, 50 câu trả lời AI mỗi ngày, phân tích chu kỳ và triệu chứng chuyên sâu, cùng trải nghiệm cặp đôi nâng cao.
                    - Premium năm: cùng tính năng với Premium tháng, khác thời hạn 365 ngày và mức tiết kiệm.
                    - Chỉ cần một người có Premium để cả hai dùng câu hỏi, lịch sử hội thoại và gợi ý chăm sóc cặp đôi nâng cao.
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
            return abdominalPainAnswer(normalized, userContext);
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

        return "";
    }

    private String productQuickAnswer(String userInput, String normalizedInput) {
        if (affiliateProductService == null || !isProductIntent(normalizedInput)) {
            return "";
        }

        List<AffiliateProduct> products = affiliateProductService.searchProductsByUserKeywords(userInput, 6);
        if (products.isEmpty()) {
            return """
                    Mình chưa tìm thấy sản phẩm đã duyệt khớp rõ với nội dung bạn vừa nhập.

                    Bạn thử ghi cụ thể hơn như "dán mụn", "túi chườm đau bụng kinh", "dung dịch vệ sinh" hoặc tên sản phẩm/shop nhé. Hi sẽ ưu tiên gợi ý từ danh sách sản phẩm đã duyệt để tránh gửi nhầm link.
                    """;
        }

        String cards = products.stream()
                .limit(3)
                .map(this::productCardLine)
                .reduce("", (left, right) -> left + right + "\n")
                .strip();
        if (cards.isBlank()) {
            return "";
        }

        String careNote = containsAny(normalizedInput, "mun", "acne", "pimple")
                ? """
                Với mụn, bạn ưu tiên làm sạch da nhẹ nhàng, không nặn mụn và dán trên vùng da khô sạch. Nếu mụn viêm lan rộng, đau nhiều hoặc để lại sẹo nhanh, bạn nên hỏi bác sĩ da liễu nhé.
                """
                : """
                Mình gợi ý theo từ khóa bạn nhập và chỉ xem đây là hỗ trợ thêm. Nếu đang có triệu chứng nặng, kéo dài hoặc diễn tiến nhanh, bạn nên ưu tiên tư vấn y tế trước nhé.
                """;

        return careNote.strip()
                + "\n\nSản phẩm phù hợp:\n"
                + cards;
    }

    private boolean isProductIntent(String normalizedInput) {
        boolean directProductAsk = containsAny(normalizedInput,
                "san pham", "goi y mua", "mua gi", "mua o dau", "link", "shop", "dat hang",
                "nen mua", "nen dung loai", "loai nao", "co loai nao", "goi y minh", "can mua", "can tim");
        boolean productKeyword = containsAny(normalizedInput,
                "dan mun", "mieng dan mun", "kem mun", "serum mun", "tui chuom", "mieng dan nhiet",
                "tra gung", "dung dich ve sinh", "coc nguyet san", "bang ve sinh", "dau bung kinh can",
                "mun", "acne", "pimple");
        return directProductAsk || productKeyword;
    }

    private String recentCyclesAnswer(String userInput, String normalizedInput, String userContext) {
        if (!isRecentCyclesIntent(normalizedInput)) {
            return "";
        }
        String cyclesLine = extractLine(userContext, "- Các kỳ gần đây:");
        if (cyclesLine.isBlank()) {
            cyclesLine = extractLine(userContext, "- CÃ¡c ká»³ gáº§n Ä‘Ã¢y:");
        }
        List<String> cycles = parseCycleRanges(cyclesLine);
        if (cycles.isEmpty()) {
            String latestLine = extractLine(userContext, "- Kỳ gần nhất:");
            if (latestLine.isBlank()) {
                latestLine = extractLine(userContext, "- Ká»³ gáº§n nháº¥t:");
            }
            if (!latestLine.isBlank() && !normalize(latestLine).contains("chua co")) {
                cycles = List.of(cleanLabel(latestLine.replaceFirst("^-\\s*Kỳ gần nhất:\\s*", "")
                        .replaceFirst("^-\\s*Ká»³ gáº§n nháº¥t:\\s*", "")));
            }
        }
        if (cycles.isEmpty()) {
            return """
                    Mình chưa thấy kỳ đã xác nhận trong dữ liệu hiện tại.

                    Bạn có thể vào phần lịch sử chu kỳ để thêm các kỳ đã ghi nhận. Khi có dữ liệu, Hi sẽ trả lời được các kỳ gần nhất chính xác hơn.
                    """;
        }

        int requestedCount = requestedCycleCount(userInput, normalizedInput);
        List<String> selectedCycles = cycles.stream()
                .filter(item -> !item.isBlank())
                .limit(Math.min(requestedCount, cycles.size()))
                .toList();
        StringBuilder answer = new StringBuilder("Mình thấy ")
                .append(selectedCycles.size())
                .append(" chu kỳ gần nhất đã ghi trong Hi là:\n\n");
        for (int index = 0; index < selectedCycles.size(); index++) {
            answer.append("- Chu kỳ ").append(index + 1).append(": ").append(selectedCycles.get(index)).append("\n");
        }
        answer.append("\nĐây là các kỳ đã được xác nhận trong lịch sử, khác với ngày dự đoán nha.");
        return answer.toString();
    }

    private boolean isRecentCyclesIntent(String normalizedInput) {
        return containsAny(normalizedInput, "chu ky gan nhat", "ky gan nhat", "chu ky gan day", "ky gan day")
                && (normalizedInput.matches(".*\\d+\\s+(chu ky|ky).*")
                || containsAny(normalizedInput, "cac chu ky", "nhung chu ky", "may chu ky", "danh sach"));
    }

    private int requestedCycleCount(String userInput, String normalizedInput) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:chu\\s*ky|ky)").matcher(normalizedInput);
        if (matcher.find()) {
            try {
                return Math.min(Math.max(Integer.parseInt(matcher.group(1)), 1), 6);
            } catch (NumberFormatException ignored) {
                return 3;
            }
        }
        return 3;
    }

    private List<String> parseCycleRanges(String cyclesLine) {
        if (cyclesLine == null || cyclesLine.isBlank()) {
            return List.of();
        }
        String value = cyclesLine.replaceFirst("^-\\s*", "").trim();
        int separator = value.indexOf(':');
        if (separator >= 0 && separator + 1 < value.length()) {
            value = value.substring(separator + 1).trim();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank() || normalize(value).contains("chua co")) {
            return List.of();
        }
        return Arrays.stream(value.split(",\\s*"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String productCardLine(AffiliateProduct product) {
        String platform = product.getPlatform() != null ? product.getPlatform().name() : "";
        return "HI_PRODUCT"
                + "|name=" + encoded(value(product.getName(), "Sản phẩm chăm sóc"))
                + "|platform=" + encoded(platform)
                + "|shop=" + encoded(product.getSourceName())
                + "|category=" + encoded(value(product.getSymptomCategory(), value(product.getCategory(), "chăm sóc tại nhà")))
                + "|tags=" + encoded(join(product.getSymptomTags()))
                + "|price=" + encoded(priceText(product.getPrice()))
                + "|image=" + encoded(product.getImageUrl())
                + "|url=" + encoded(product.getAffiliateUrl());
    }

    private String encoded(String value) {
        return URLEncoder.encode(value(value, ""), StandardCharsets.UTF_8);
    }

    private String priceText(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return "";
        return value.stripTrailingZeros().toPlainString();
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    private String affiliateLines(String context) {
        String lines = extractBlock(context, "Sản phẩm gợi ý đã duyệt");
        if (lines.isBlank()) {
            lines = extractBlock(context, "Sản phẩm affiliate đã duyệt");
        }
        if (lines.isBlank()) {
            return "";
        }
        String cards = lines.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("HI_PRODUCT|") || line.startsWith("-"))
                .limit(2)
                .map(line -> line.startsWith("-") ? legacyProductLine(line) : line)
                .filter(line -> !line.isBlank())
                .reduce("", (a, b) -> a + b + "\n");
        if (cards.isBlank()) {
            return "";
        }
        return cards.strip();
    }

    private String abdominalPainAnswer(String normalizedInput, String userContext) {
        boolean asksForMethods = containsAny(normalizedInput,
                "phuong phap", "cach giam", "giam dau", "tai nha", "nen lam gi", "xu ly", "lam sao");

        if (!asksForMethods) {
            return """
                    Mình ở đây với bạn nè. Trước mắt bạn có thể:

                    - Chườm ấm vùng bụng dưới 15-20 phút.
                    - Uống nước ấm, nghỉ ở tư thế dễ chịu và vận động thật nhẹ nếu thấy ổn.
                    - Theo dõi mức đau và lượng máu để nhận ra thay đổi bất thường.

                    Để mình gợi ý sát hơn: đau âm ỉ hay quặn từng cơn, mức đau mấy trên 10, và có sốt/chóng mặt/ra máu nhiều bất thường không?

                    Nếu đau dữ dội đột ngột, ngất, sốt, nôn liên tục hoặc ra máu nhiều bất thường, bạn nên liên hệ bác sĩ/cơ sở y tế sớm nhé.
                    """;
        }

        String products = affiliateLines(userContext);
        String productSection = products.isBlank()
                ? ""
                : "\n\nSản phẩm hỗ trợ tùy chọn:\n" + products;
        return """
                Bạn có thể thử các cách giảm đau bụng kỳ kinh tại nhà theo thứ tự này:

                - Chườm ấm bụng dưới hoặc lưng dưới 15-20 phút, nghỉ vài phút rồi lặp lại nếu cần.
                - Uống nước ấm, ăn nhẹ và hạn chế rượu bia, cà phê hoặc món quá mặn nếu chúng làm bạn khó chịu hơn.
                - Đi bộ chậm, kéo giãn nhẹ vùng hông-lưng hoặc nằm nghiêng co chân nếu tư thế đó giúp dễ chịu.
                - Ngủ đủ và ghi mức đau theo thang 0-10 để theo dõi cách nào có hiệu quả.

                Để cá nhân hóa thêm: bạn đau âm ỉ hay quặn từng cơn, mức đau mấy trên 10, và có sốt/chóng mặt/ra máu nhiều bất thường không?

                Nếu đau dữ dội đột ngột, ngất, sốt, nôn liên tục, đau lệch một bên hoặc ra máu nhiều bất thường, bạn nên liên hệ bác sĩ/cơ sở y tế sớm.%s
                """.formatted(productSection);
    }

    private String enforceAnswerPolicy(String userInput, String answer) {
        if (answer == null || answer.isBlank()) {
            return fallbackTemporaryUnavailable();
        }

        List<String> bodyLines = new ArrayList<>();
        List<String> productLines = new ArrayList<>();
        for (String line : answer.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("HI_PRODUCT|")) {
                productLines.add(trimmed);
                continue;
            }
            if (isProductHeading(trimmed)) {
                continue;
            }
            bodyLines.add(line);
        }

        String body = String.join("\n", bodyLines).strip();
        if (productLines.isEmpty()) {
            return body;
        }

        String normalizedInput = normalize(userInput);
        boolean asksForProduct = containsAny(normalizedInput,
                "san pham", "mua gi", "goi y mua", "tui chuom", "mieng dan", "tra gung");
        boolean asksForAdvice = containsAny(normalizedInput,
                "phuong phap", "cach", "nen lam gi", "xu ly", "giam", "tai nha");
        boolean hasSubstantialAdvice = body.length() >= 220
                && containsAny(normalize(body), "ban co the", "thu ", "chuom", "nghi", "theo doi");

        if ((!asksForProduct && !asksForAdvice) || (!asksForProduct && !hasSubstantialAdvice)) {
            return body;
        }

        return body
                + "\n\nSản phẩm hỗ trợ tùy chọn:\n"
                + String.join("\n", productLines.stream().limit(2).toList());
    }

    private boolean isProductHeading(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("san pham goi y")
                || normalized.startsWith("san pham ho tro tuy chon")
                || normalized.startsWith("mot vai san pham ho tro");
    }

    private String legacyProductLine(String line) {
        String[] parts = line.replaceFirst("^-\\s*", "").split("\\|");
        if (parts.length == 0) return "";
        String name = parts[0].trim();
        String platform = "";
        String tags = "";
        String url = "";
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith("nền tảng:")) platform = item.replaceFirst("nền tảng:\\s*", "").trim();
            if (item.startsWith("tags:")) tags = item.replaceFirst("tags:\\s*", "").trim();
            if (item.startsWith("link:")) url = item.replaceFirst("link:\\s*", "").trim();
        }
        return "HI_PRODUCT|name=" + name + "|platform=" + platform + "|tags=" + tags + "|url=" + url;
    }

    private String fallbackUnavailable() {
        return """
                Hi AI đang cần cấu hình nhà cung cấp AI để trả lời sâu hơn.

                Mình vẫn có thể hỗ trợ các câu hỏi cơ bản về Hi, gói Free/Premium, thông báo, video, sản phẩm hỗ trợ và dữ liệu chu kỳ đã lưu.

                Nếu có triệu chứng nghiêm trọng, hãy liên hệ bác sĩ hoặc cơ sở y tế nhé.
                """;
    }

    private String fallbackTemporaryUnavailable() {
        return """
                Hi AI đang kết nối chậm hơn bình thường nên chưa thể hoàn tất câu trả lời này.

                Bạn thử gửi lại sau ít phút nhé. Nếu câu hỏi liên quan đến triệu chứng, hãy cho mình biết vị trí khó chịu, mức độ từ 0-10 và các dấu hiệu đi kèm để mình hỗ trợ an toàn hơn ở lần gửi tiếp theo.

                Nếu có triệu chứng nghiêm trọng hoặc diễn tiến nhanh, hãy liên hệ bác sĩ hoặc cơ sở y tế.
                """;
    }

    private String sanitizeDailyTip(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String cleaned = answer
                .replace("```", "")
                .replaceAll("(?m)^\\s*[\"']|[\"']\\s*$", "")
                .replaceAll("[\\t\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        String normalized = normalize(cleaned);
        if (containsAny(normalized,
                "trieu chung trong ky",
                "nhat ky gan day",
                "mood score",
                "flow intensity",
                "hi_product|",
                "du lieu user",
                "ky tiep theo uoc tinh",
                "do tin cay du doan")) {
            return "";
        }
        long wordCount = Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !word.isBlank())
                .count();
        return wordCount <= 80 ? cleaned : "";
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
        return noAccent
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
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

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
