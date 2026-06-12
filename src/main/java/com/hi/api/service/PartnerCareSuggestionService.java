package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.model.CoupleQuestionSession;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.PartnerCareSuggestion;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.User;
import com.hi.api.repository.CoupleQuestionSessionRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.PartnerCareSuggestionRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class PartnerCareSuggestionService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PartnerCareSuggestionRepository suggestionRepository;
    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository symptomRepository;
    private final SymptomDictionaryRepository dictionaryRepository;
    private final CoupleQuestionSessionRepository sessionRepository;
    private final CycleRecordService cycleRecordService;
    private final PartnerAccessService partnerAccessService;
    private final NotificationService notificationService;
    private final SubscriptionAccessService subscriptionAccessService;

    public PartnerCareSuggestionService(PartnerCareSuggestionRepository suggestionRepository,
                                        DailyLogRepository dailyLogRepository,
                                        DailyLogSymptomRepository symptomRepository,
                                        SymptomDictionaryRepository dictionaryRepository,
                                        CoupleQuestionSessionRepository sessionRepository,
                                        CycleRecordService cycleRecordService,
                                        PartnerAccessService partnerAccessService,
                                        NotificationService notificationService,
                                        SubscriptionAccessService subscriptionAccessService) {
        this.suggestionRepository = suggestionRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.symptomRepository = symptomRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.sessionRepository = sessionRepository;
        this.cycleRecordService = cycleRecordService;
        this.partnerAccessService = partnerAccessService;
        this.notificationService = notificationService;
        this.subscriptionAccessService = subscriptionAccessService;
    }

    public PartnerCareSuggestion getToday(String userId) {
        User recipient = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(recipient);
        subscriptionAccessService.requireCouplePremium(recipient, partner);
        if (Boolean.FALSE.equals(partnerAccessService.notificationPreferences(recipient).getContextualCareSuggestionsEnabled())) {
            throw new IllegalArgumentException("Bạn đang tắt Gợi ý quan tâm theo ngữ cảnh");
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        PartnerCareSuggestion existing = suggestionRepository.findByRecipientUserIdAndSuggestionDate(userId, today).orElse(null);
        if (existing != null && isStillAllowed(existing, partner)) return existing;
        if (existing != null) suggestionRepository.delete(existing);
        return generate(recipient, partner, today);
    }

    public PartnerCareSuggestion generate(User recipient, User partner, LocalDate date) {
        subscriptionAccessService.requireCouplePremium(recipient, partner);
        PartnerCareSuggestion existing = suggestionRepository
                .findByRecipientUserIdAndSuggestionDate(recipient.getId(), date).orElse(null);
        if (existing != null) return existing;
        User.PartnerSharingPreferences sharing = partnerAccessService.sharingPreferences(partner);
        PartnerCareSuggestion suggestion = new PartnerCareSuggestion();
        suggestion.setPairKey(partnerAccessService.pairKey(recipient.getId(), partner.getId()));
        suggestion.setRecipientUserId(recipient.getId());
        suggestion.setPartnerUserId(partner.getId());
        suggestion.setSuggestionDate(date);
        String partnerName = partner.getName() == null || partner.getName().isBlank() ? "Người ấy" : partner.getName();

        DailyLog latest = dailyLogRepository.findByUserIdOrderByLogDateDesc(partner.getId()).stream().findFirst().orElse(null);
        if (latest != null && Boolean.TRUE.equals(sharing.getShareDetailedSymptoms())) {
            List<DailyLogSymptom> relations = symptomRepository.findByDailyLogId(latest.getId());
            List<String> symptoms = dictionaryRepository.findAllById(
                            relations.stream().map(DailyLogSymptom::getSymptomId).distinct().toList())
                    .stream().map(SymptomDictionary::getName).filter(Objects::nonNull).limit(3).toList();
            if (!symptoms.isEmpty()) {
                suggestion.setSourceType("SYMPTOM");
                suggestion.setPriority(400);
                suggestion.setReason(partnerName + " đã chia sẻ gần đây: " + String.join(", ", symptoms) + ".");
                suggestion.setAction("Hỏi xem Người ấy muốn được nghỉ ngơi, lắng nghe hay hỗ trợ một việc cụ thể.");
                suggestion.setMessageTemplate("Hôm nay bạn thấy trong người thế nào? Mình có thể giúp bạn việc gì nhỏ không?");
                return saveAndNotify(suggestion);
            }
        }
        if (latest != null && Boolean.TRUE.equals(sharing.getShareHealthNotes())
                && latest.getNotes() != null && !latest.getNotes().isBlank()) {
            suggestion.setSourceType("NOTE");
            suggestion.setPriority(350);
            String note = latest.getNotes().trim();
            String excerpt = note.length() > 120 ? note.substring(0, 120) + "..." : note;
            suggestion.setReason(partnerName + " đã cho phép chia sẻ ghi chú: “" + excerpt + "”");
            suggestion.setAction("Phản hồi bằng sự lắng nghe, không tự chẩn đoán hoặc đưa ra kết luận y khoa.");
            suggestion.setMessageTemplate("Mình đã đọc điều bạn chia sẻ. Bạn muốn mình lắng nghe hay hỗ trợ một việc cụ thể?");
            return saveAndNotify(suggestion);
        }
        if (latest != null && Boolean.TRUE.equals(sharing.getShareMood()) && latest.getMoodScore() != null) {
            suggestion.setSourceType("MOOD");
            suggestion.setPriority(300);
            suggestion.setReason(partnerName + " đang có cập nhật cảm xúc ở mức " + moodLabel(latest.getMoodScore()) + ".");
            suggestion.setAction(latest.getMoodScore() <= 2
                    ? "Ưu tiên lắng nghe và tránh thúc ép Người ấy giải thích ngay."
                    : "Gửi một lời hỏi thăm ngắn để duy trì kết nối tích cực.");
            suggestion.setMessageTemplate("Mình thấy hôm nay bạn có cập nhật cảm xúc. Bạn muốn mình lắng nghe hay cùng làm gì đó nhẹ nhàng?");
            return saveAndNotify(suggestion);
        }
        if (Boolean.TRUE.equals(sharing.getShareCycleData()) && "female".equalsIgnoreCase(partner.getGender())) {
            CycleRecordInsightResponse insights = cycleRecordService.getInsights(partner.getId());
            if ("CONFIRMED".equalsIgnoreCase(insights.getPeriodStatus())
                    || "DELAYED".equalsIgnoreCase(insights.getPeriodStatus())
                    || (insights.getDaysUntilEstimatedPeriod() != null && insights.getDaysUntilEstimatedPeriod() <= 3)) {
                suggestion.setSourceType("CYCLE");
                suggestion.setPriority(200);
                suggestion.setReason("Trạng thái chu kỳ được chia sẻ cho thấy " + partnerName + " có thể cần thêm sự quan tâm.");
                suggestion.setAction("Đề nghị hỗ trợ một việc nhỏ và nhắc rằng dự đoán chu kỳ chỉ mang tính tham khảo.");
                suggestion.setMessageTemplate("Mình ở đây nếu hôm nay bạn cần nghỉ ngơi hoặc muốn mình giúp một việc nhỏ nhé.");
                return saveAndNotify(suggestion);
            }
        }

        CoupleQuestionSession recent = sessionRepository
                .findFirstByPairKeyAndUnlockedAtIsNotNullOrderByQuestionDateDesc(suggestion.getPairKey()).orElse(null);
        suggestion.setSourceType(recent != null ? "QUESTION" : "GENERAL");
        suggestion.setPriority(recent != null ? 100 : 10);
        suggestion.setReason(recent != null
                ? "Hai bạn vừa hoàn thành một câu hỏi thuộc chủ đề " + recent.getCategory() + "."
                : "Hôm nay chưa có tín hiệu sức khỏe nào được Người ấy cho phép chia sẻ.");
        suggestion.setAction("Bắt đầu bằng một lời hỏi thăm không tạo áp lực.");
        suggestion.setMessageTemplate("Hôm nay của bạn thế nào? Mình muốn nghe nếu bạn sẵn sàng chia sẻ.");
        return saveAndNotify(suggestion);
    }

    private PartnerCareSuggestion saveAndNotify(PartnerCareSuggestion suggestion) {
        PartnerCareSuggestion saved = suggestionRepository.save(suggestion);
        notificationService.createIdempotentNotification(
                saved.getRecipientUserId(),
                "CONTEXTUAL_CARE_SUGGESTION",
                "Một gợi ý quan tâm dành cho hôm nay",
                "Hi có một hành động nhỏ giúp bạn quan tâm Người ấy tinh tế hơn.",
                "/settings/notifications?tab=today",
                "CARE_SUGGESTION:" + saved.getRecipientUserId() + ":" + saved.getSuggestionDate(),
                java.util.Map.of("suggestionId", saved.getId(), "date", saved.getSuggestionDate().toString())
        );
        return saved;
    }

    private String moodLabel(int mood) {
        return switch (Math.max(1, Math.min(5, mood))) {
            case 1 -> "rất thấp";
            case 2 -> "hơi mệt hoặc lo lắng";
            case 4 -> "khá tích cực";
            case 5 -> "rất tích cực";
            default -> "ổn định";
        };
    }

    private boolean isStillAllowed(PartnerCareSuggestion suggestion, User partner) {
        User.PartnerSharingPreferences sharing = partnerAccessService.sharingPreferences(partner);
        return switch (suggestion.getSourceType()) {
            case "SYMPTOM" -> Boolean.TRUE.equals(sharing.getShareDetailedSymptoms());
            case "NOTE" -> Boolean.TRUE.equals(sharing.getShareHealthNotes());
            case "MOOD" -> Boolean.TRUE.equals(sharing.getShareMood());
            case "CYCLE" -> Boolean.TRUE.equals(sharing.getShareCycleData());
            default -> true;
        };
    }
}
