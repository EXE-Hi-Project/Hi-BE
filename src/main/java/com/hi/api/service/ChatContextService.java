package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.User;
import com.hi.api.repository.AffiliateProductRepository;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatContextService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UserRepository userRepository;
    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final CycleRecordService cycleRecordService;
    private final AffiliateProductRepository affiliateProductRepository;

    public ChatContextService(UserRepository userRepository,
                              CycleRecordRepository cycleRecordRepository,
                              DailyLogRepository dailyLogRepository,
                              DailyLogSymptomRepository dailyLogSymptomRepository,
                              SymptomDictionaryRepository symptomDictionaryRepository,
                              CycleRecordService cycleRecordService,
                              AffiliateProductRepository affiliateProductRepository) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.cycleRecordService = cycleRecordService;
        this.affiliateProductRepository = affiliateProductRepository;
    }

    public String buildContext(String userId) {
        Optional<User> maybeUser = userRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            return productContext();
        }

        User user = maybeUser.get();
        StringBuilder context = new StringBuilder(productContext());
        context.append("\nAI response style preference: ")
                .append(user.getNotificationPreferences() != null
                        ? value(user.getNotificationPreferences().getAiResponseStyle(), value(user.getAiTone(), "FRIENDLY"))
                        : value(user.getAiTone(), "FRIENDLY"))
                .append(". Adjust tone accordingly: FRIENDLY warm, PLAYFUL lightly witty, SCIENTIFIC evidence-first, CONCISE brief, CARE_PARTNER partner-care oriented.\n");
        context.append("\n\nDữ liệu user hiện tại:\n");
        context.append("- Tên: ").append(value(user.getName(), "người dùng")).append("\n");
        context.append("- Giới tính: ").append(value(user.getGender(), "chưa rõ")).append("\n");
        context.append("- Mục tiêu onboarding: ").append(join(user.getGoals())).append("\n");
        context.append("- Sở thích onboarding: ").append(join(user.getInterests())).append("\n");
        context.append("- Gói hiện tại: ").append(user.getSubscription() != null ? value(user.getSubscription().getPlan(), "free") : "free").append("\n");
        context.append("- Nhắc kỳ kinh: ").append(Boolean.TRUE.equals(user.getPeriodReminder()) ? "bật" : "tắt").append("\n");
        context.append("- Email nhắc nhở: ").append(user.getNotificationPreferences() != null && Boolean.TRUE.equals(user.getNotificationPreferences().getEmailEnabled()) ? "bật" : "tắt").append("\n");

        appendCycleContext(context, "Dữ liệu chu kỳ của user", user.getId());
        appendRecentLogs(context, user.getId(), "Nhật ký gần đây của user");

        if (user.getPartnerId() != null && !user.getPartnerId().isBlank()) {
            userRepository.findById(user.getPartnerId()).ifPresent(partner -> {
                context.append("\nDữ liệu Người ấy đã kết nối:\n");
                context.append("- Tên: ").append(value(partner.getName(), "Người ấy")).append("\n");
                context.append("- Giới tính: ").append(value(partner.getGender(), "chưa rõ")).append("\n");
                if ("female".equalsIgnoreCase(partner.getGender())) {
                    appendCycleContext(context, "Chu kỳ của Người ấy", partner.getId());
                }
                dailyLogRepository.findFirstByUserIdAndMoodScoreIsNotNullOrderByLogDateDesc(partner.getId())
                        .ifPresent(log -> appendDailyLog(context, "Cảm xúc Người ấy chia sẻ gần nhất", log, symptomMap()));
            });
        }

        appendAffiliateContext(context);
        return context.toString();
    }

    private String productContext() {
        return """
                Thông tin sản phẩm Hi:
                - Hi là ứng dụng theo dõi sức khỏe sinh sản cho người dùng Việt Nam, dành cho cả nữ và nam.
                - Tính năng chính: onboarding, theo dõi chu kỳ, lịch sử chu kỳ, triệu chứng, cảm xúc, Người ấy, thông báo web, email nhắc nhở, video sức khỏe được duyệt, affiliate sản phẩm hỗ trợ tại nhà và Hi AI.
                - Gói Free: theo dõi chu kỳ cơ bản, lịch sử cá nhân, nhắc cơ bản và AI giới hạn.
                - Premium tháng: analytics nâng cao, AI Premium và chia sẻ Người ấy nâng cao.
                - Premium năm: toàn bộ Premium tháng, báo cáo định kỳ và ưu đãi tiết kiệm.
                - Video trong Hi là video YouTube công khai từ nguồn được duyệt, không tải xuống hoặc rehost.
                - Affiliate TikTok/Shopee: Hi có thể gợi ý sản phẩm hỗ trợ như túi chườm, miếng dán ấm, trà gừng. Khi có link affiliate, phải nói rõ Hi có thể nhận hoa hồng.
                - Trang trợ giúp: /help. Điều khoản: /terms. Chính sách bảo mật: /privacy.
                - Email liên hệ: hilover.space@gmail.com.
                - Dự đoán chu kỳ, rụng trứng và cửa sổ thụ thai chỉ mang tính tham khảo, không thay thế biện pháp tránh thai hoặc tư vấn y khoa.
                """;
    }

    private void appendCycleContext(StringBuilder context, String title, String userId) {
        List<CycleRecord> cycles = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .limit(6)
                .toList();
        CycleRecordInsightResponse insights = cycleRecordService.getInsights(userId);

        context.append("\n").append(title).append(":\n");
        if (cycles.isEmpty()) {
            context.append("- Chưa có kỳ đã xác nhận.\n");
        } else {
            CycleRecord latest = cycles.get(0);
            context.append("- Kỳ gần nhất: ").append(range(latest)).append("\n");
            context.append("- Các kỳ gần đây: ").append(cycles.stream().map(this::range).toList()).append("\n");
            appendPeriodSymptoms(context, latest, userId);
        }
        context.append("- Trạng thái kỳ: ").append(value(insights.getPeriodStatus(), "UNKNOWN")).append("\n");
        context.append("- Đánh giá chu kỳ: ").append(value(insights.getRegularityLabel(), "chưa đủ dữ liệu")).append("\n");
        context.append("- Lý do đánh giá: ").append(join(insights.getRegularityReasons())).append("\n");
        context.append("- Kỳ tiếp theo ước tính: ")
                .append(date(insights.getEstimatedPeriodStartDate()))
                .append(" - ")
                .append(date(insights.getEstimatedPeriodEndDate()))
                .append("\n");
        context.append("- Rụng trứng ước tính: ").append(date(insights.getEstimatedOvulationDate())).append("\n");
        context.append("- Cửa sổ thụ thai ước tính: ")
                .append(date(insights.getFertileWindowStartDate()))
                .append(" - ")
                .append(date(insights.getFertileWindowEndDate()))
                .append("\n");
        context.append("- Khả năng thụ thai ước tính: ").append(value(insights.getFertilityStatus(), "UNKNOWN")).append("\n");
        context.append("- Độ tin cậy dự đoán: ").append(value(insights.getPredictionConfidence(), "LOW")).append("\n");
    }

    private void appendPeriodSymptoms(StringBuilder context, CycleRecord latest, String userId) {
        LocalDate start = latest.getStartDate();
        int periodLength = latest.getPeriodLength() != null ? latest.getPeriodLength() : 5;
        LocalDate end = latest.getEndDate() != null ? latest.getEndDate() : latest.getStartDate().plusDays(Math.max(1, periodLength) - 1L);
        List<DailyLog> logs = dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, start, end);
        if (logs.isEmpty()) {
            context.append("- Triệu chứng trong kỳ gần nhất: chưa có nhật ký triệu chứng.\n");
            return;
        }

        Map<Long, SymptomDictionary> dictionary = symptomMap();
        context.append("- Triệu chứng trong kỳ gần nhất:\n");
        logs.stream()
                .sorted((a, b) -> a.getLogDate().compareTo(b.getLogDate()))
                .limit(10)
                .forEach(log -> appendDailyLog(context, "  + Ngày " + date(log.getLogDate()), log, dictionary));
    }

    private void appendRecentLogs(StringBuilder context, String userId, String title) {
        List<DailyLog> logs = dailyLogRepository.findByUserIdOrderByLogDateDesc(userId).stream()
                .limit(5)
                .toList();
        if (logs.isEmpty()) {
            context.append("\n").append(title).append(": chưa có nhật ký.\n");
            return;
        }
        Map<Long, SymptomDictionary> dictionary = symptomMap();
        context.append("\n").append(title).append(":\n");
        logs.forEach(log -> appendDailyLog(context, "- Nhật ký " + date(log.getLogDate()), log, dictionary));
    }

    private void appendDailyLog(StringBuilder context, String title, DailyLog log, Map<Long, SymptomDictionary> dictionary) {
        context.append(title).append(":\n");
        context.append("  - Lượng kinh: ").append(log.getFlowIntensity()).append("\n");
        context.append("  - Mood score: ").append(log.getMoodScore() != null ? log.getMoodScore() : "--").append("\n");
        context.append("  - Có cục máu đông: ").append(Boolean.TRUE.equals(log.getHasClots()) ? "có" : "không").append("\n");

        List<DailyLogSymptom> symptoms = dailyLogSymptomRepository.findByDailyLogId(log.getId());
        if (!symptoms.isEmpty()) {
            String symptomText = symptoms.stream()
                    .map(item -> {
                        SymptomDictionary symptom = dictionary.get(item.getSymptomId());
                        String name = symptom != null ? symptom.getName() : "triệu chứng #" + item.getSymptomId();
                        return name + " (" + item.getSeverity() + ")";
                    })
                    .collect(Collectors.joining(", "));
            context.append("  - Triệu chứng: ").append(symptomText).append("\n");
        }
        if (log.getNotes() != null && !log.getNotes().isBlank()) {
            context.append("  - Ghi chú: ").append(log.getNotes()).append("\n");
        }
    }

    private void appendAffiliateContext(StringBuilder context) {
        List<AffiliateProduct> products = affiliateProductRepository.findByIsActiveTrueOrderByCommissionRateDescPriceAsc()
                .stream()
                .limit(8)
                .toList();
        if (products.isEmpty()) {
            context.append("\nSản phẩm affiliate: chưa có sản phẩm đã duyệt.\n");
            return;
        }
        context.append("\nSản phẩm affiliate đã duyệt để gợi ý nhẹ nhàng khi phù hợp:\n");
        for (AffiliateProduct product : products) {
            context.append("- ")
                    .append(value(product.getName(), "Sản phẩm"))
                    .append(" | nền tảng: ").append(product.getPlatform())
                    .append(" | nhóm: ").append(value(product.getSymptomCategory(), value(product.getCategory(), "chăm sóc tại nhà")))
                    .append(" | tags: ").append(join(product.getSymptomTags()))
                    .append(" | link: ").append(value(product.getAffiliateUrl(), "chưa có link"))
                    .append("\n");
        }
    }

    private Map<Long, SymptomDictionary> symptomMap() {
        return symptomDictionaryRepository.findAll().stream()
                .collect(Collectors.toMap(SymptomDictionary::getId, item -> item, (a, b) -> a));
    }

    private String range(CycleRecord record) {
        return date(record.getStartDate()) + " - " + date(record.getEndDate());
    }

    private String date(LocalDate value) {
        return value == null ? "--" : value.format(DATE);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? "chưa có" : String.join(", ", values);
    }
}
