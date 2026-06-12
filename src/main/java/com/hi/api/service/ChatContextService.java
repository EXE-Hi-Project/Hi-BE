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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;

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
    private final PartnerAccessService partnerAccessService;

    public ChatContextService(UserRepository userRepository,
                              CycleRecordRepository cycleRecordRepository,
                              DailyLogRepository dailyLogRepository,
                              DailyLogSymptomRepository dailyLogSymptomRepository,
                              SymptomDictionaryRepository symptomDictionaryRepository,
                              CycleRecordService cycleRecordService,
                              AffiliateProductRepository affiliateProductRepository,
                              PartnerAccessService partnerAccessService) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.cycleRecordService = cycleRecordService;
        this.affiliateProductRepository = affiliateProductRepository;
        this.partnerAccessService = partnerAccessService;
    }

    @Cacheable(value = "ai_context", key = "#userId")
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

        appendCycleContext(context, "Dữ liệu chu kỳ của user", user.getId(), true);
        appendRecentLogs(context, user.getId(), "Nhật ký gần đây của user");

        if (user.getPartnerId() != null && !user.getPartnerId().isBlank()) {
            try {
                User partner = partnerAccessService.requireCurrentPartner(user);
                context.append("\nDữ liệu Người ấy đã kết nối:\n");
                context.append("- Tên: ").append(value(partner.getName(), "Người ấy")).append("\n");
                context.append("- Giới tính: ").append(value(partner.getGender(), "chưa rõ")).append("\n");
                if (partnerAccessService.canShareCycleData(partner) && "female".equalsIgnoreCase(partner.getGender())) {
                    appendCycleContext(context, "Chu kỳ của Người ấy", partner.getId(), false);
                }
                if (partnerAccessService.canShareMood(partner)) {
                    dailyLogRepository.findFirstByUserIdAndMoodScoreIsNotNullOrderByLogDateDesc(partner.getId())
                            .ifPresent(log -> appendMoodSummary(context, "Cảm xúc Người ấy chia sẻ gần nhất", log));
                }
            } catch (RuntimeException ex) {
                context.append("\nDữ liệu Người ấy: liên kết không hợp lệ hoặc chưa được chia sẻ.\n");
            }
        }

        appendAffiliateContext(context);
        return context.toString();
    }

    private String productContext() {
        return """
                Thông tin sản phẩm Hi:
                - Hi là ứng dụng theo dõi sức khỏe sinh sản cho người dùng Việt Nam, dành cho cả nữ và nam.
                - Tính năng chính: onboarding, theo dõi chu kỳ, lịch sử chu kỳ, triệu chứng, cảm xúc, Người ấy, thông báo web, email nhắc nhở, video sức khỏe được duyệt, sản phẩm hỗ trợ tại nhà và Hi AI.
                - Gói Free: đầy đủ theo dõi và lịch sử sức khỏe, dự đoán cơ bản, cảnh báo an toàn, mọi phong cách AI, email và lịch nhắc tùy chỉnh; tối đa 5 câu trả lời AI mỗi ngày.
                - Premium tháng: toàn bộ Free, 50 câu trả lời AI mỗi ngày, phân tích chu kỳ và triệu chứng chuyên sâu, cùng trải nghiệm cặp đôi nâng cao.
                - Premium năm: cùng tính năng với Premium tháng, khác thời hạn 365 ngày và mức tiết kiệm.
                - Chỉ cần một người có Premium để cả hai dùng tính năng cặp đôi nâng cao.
                - Video trong Hi là video YouTube công khai từ nguồn được duyệt, không tải xuống hoặc rehost.
                - Sản phẩm hỗ trợ: Hi có thể gợi ý túi chườm, miếng dán ấm, trà gừng hoặc món chăm sóc phù hợp khi có dữ liệu sản phẩm đã duyệt.
                - Trang trợ giúp: /help. Điều khoản: /terms. Chính sách bảo mật: /privacy.
                - Email liên hệ: hilover.space@gmail.com.
                - Dự đoán chu kỳ, rụng trứng và cửa sổ thụ thai chỉ mang tính tham khảo, không thay thế biện pháp tránh thai hoặc tư vấn y khoa.
                """;
    }

    private void appendCycleContext(StringBuilder context, String title, String userId, boolean includePeriodSymptoms) {
        List<CycleRecord> cycles = cycleRecordRepository
                .findByUserIdOrderByStartDateDesc(userId, PageRequest.of(0, 6))
                .getContent();
        CycleRecordInsightResponse insights = cycleRecordService.getInsights(userId);

        context.append("\n").append(title).append(":\n");
        if (cycles.isEmpty()) {
            context.append("- Chưa có kỳ đã xác nhận.\n");
        } else {
            CycleRecord latest = cycles.get(0);
            context.append("- Kỳ gần nhất: ").append(range(latest)).append("\n");
            context.append("- Các kỳ gần đây: ").append(cycles.stream().map(this::range).toList()).append("\n");
            if (includePeriodSymptoms) {
                appendPeriodSymptoms(context, latest, userId);
            }
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
        List<DailyLog> logs = distinctLogsByDate(dailyLogRepository.findByUserIdOrderByLogDateDesc(userId));
        if (start != null || end != null) {
            final LocalDate finalStart = start;
            final LocalDate finalEnd = end;
            logs = logs.stream()
                    .filter(logItem -> {
                        LocalDate d = logItem.getLogDate();
                        if (d == null) return false;
                        boolean afterStart = (finalStart == null || !d.isBefore(finalStart));
                        boolean beforeEnd = (finalEnd == null || !d.isAfter(finalEnd));
                        return afterStart && beforeEnd;
                    })
                    .collect(Collectors.toList());
        }
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
        List<DailyLog> logs = dailyLogRepository
                .findByUserIdOrderByLogDateDesc(userId, PageRequest.of(0, 20))
                .getContent();
        logs = distinctLogsByDate(logs).stream().limit(5).toList();
        if (logs.isEmpty()) {
            context.append("\n").append(title).append(": chưa có nhật ký.\n");
            return;
        }
        Map<Long, SymptomDictionary> dictionary = symptomMap();
        context.append("\n").append(title).append(":\n");
        logs.forEach(log -> appendDailyLog(context, "- Nhật ký " + date(log.getLogDate()), log, dictionary));
    }

    private void appendMoodSummary(StringBuilder context, String title, DailyLog log) {
        context.append(title).append(":\n");
        context.append("  - Mood score: ").append(log.getMoodScore() != null ? log.getMoodScore() : "--").append("\n");
        context.append("  - Ngày ghi nhận: ").append(date(log.getLogDate())).append("\n");
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
                .limit(5)
                .toList();
        if (products.isEmpty()) {
            context.append("\nSản phẩm gợi ý: chưa có sản phẩm đã duyệt.\n");
            return;
        }
        context.append("\nSản phẩm gợi ý đã duyệt để dùng khi thật sự phù hợp. Nếu dùng, chép nguyên dòng HI_PRODUCT tương ứng, không tự viết URL:\n");
        for (AffiliateProduct product : products) {
            context.append(productCardLine(product)).append("\n");
        }
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

    private Map<Long, SymptomDictionary> symptomMap() {
        return symptomDictionaryRepository.findAll().stream()
                .collect(Collectors.toMap(SymptomDictionary::getId, item -> item, (a, b) -> a));
    }

    private List<DailyLog> distinctLogsByDate(List<DailyLog> logs) {
        Map<LocalDate, DailyLog> byDate = new LinkedHashMap<>();
        logs.stream()
                .filter(log -> log.getLogDate() != null)
                .sorted(Comparator
                        .comparing(DailyLog::getLogDate, Comparator.reverseOrder())
                        .thenComparing(
                                log -> log.getUpdatedAt() != null ? log.getUpdatedAt() : Instant.EPOCH,
                                Comparator.reverseOrder()
                        ))
                .forEach(log -> byDate.putIfAbsent(log.getLogDate(), log));
        return List.copyOf(byDate.values());
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
