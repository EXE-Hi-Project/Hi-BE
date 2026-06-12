package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SubscriptionAccessService {

    public static final int FREE_AI_DAILY_LIMIT = 5;
    public static final int PREMIUM_AI_DAILY_LIMIT = 50;

    private final UserRepository userRepository;

    public SubscriptionAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public SubscriptionAccess getAccess(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        return getAccess(user);
    }

    public SubscriptionAccess getAccess(User user) {
        User.SubscriptionInfo subscription = user.getSubscription();
        String plan = normalizePlan(subscription != null ? subscription.getPlan() : null);
        Instant activeUntil = subscription != null ? subscription.getCurrentPeriodEnd() : null;
        String status = subscription != null && subscription.getStatus() != null
                ? subscription.getStatus().trim().toLowerCase(Locale.ROOT)
                : "";
        boolean withinPaidPeriod = activeUntil != null && activeUntil.isAfter(Instant.now());
        boolean premium = !"FREE".equals(plan)
                && (("active".equals(status) || "trialing".equals(status)) && (activeUntil == null || withinPaidPeriod)
                || "canceled".equals(status) && withinPaidPeriod);
        String tier = premium ? "PREMIUM" : "FREE";
        int aiDailyLimit = premium ? PREMIUM_AI_DAILY_LIMIT : FREE_AI_DAILY_LIMIT;
        return new SubscriptionAccess(
                tier,
                premium ? plan : "FREE",
                premium,
                activeUntil,
                subscription != null && Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                aiDailyLimit,
                entitlements(premium)
        );
    }

    public boolean hasPremium(String userId) {
        return getAccess(userId).premium();
    }

    public boolean hasCouplePremium(User user, User partner) {
        return getAccess(user).premium() || getAccess(partner).premium();
    }

    public boolean hasPremiumForCouple(User user) {
        if (getAccess(user).premium()) return true;
        if (user.getPartnerId() == null || user.getPartnerId().isBlank()) return false;
        return userRepository.findById(user.getPartnerId())
                .filter(partner -> user.getId().equals(partner.getPartnerId()))
                .map(partner -> getAccess(partner).premium())
                .orElse(false);
    }

    public Map<String, Boolean> getEffectiveEntitlements(User user) {
        Map<String, Boolean> values = new LinkedHashMap<>(getAccess(user).entitlements());
        boolean couplePremium = hasPremiumForCouple(user);
        values.put("coupleDailyQuestions", couplePremium);
        values.put("coupleQuestionHistory", couplePremium);
        values.put("coupleConversation", couplePremium);
        values.put("contextualPartnerCare", couplePremium);
        values.put("partnerCareReminders", couplePremium);
        return values;
    }

    public void requireCouplePremium(User user, User partner) {
        if (!hasCouplePremium(user, partner)) {
            throw new AccessDeniedException("Tính năng cặp đôi nâng cao yêu cầu một trong hai tài khoản có Premium");
        }
    }

    public CycleRecordInsightResponse filterInsights(CycleRecordInsightResponse insights, boolean premium) {
        if (insights == null) return null;
        if (premium) {
            return insights.toBuilder().advancedAnalyticsAvailable(true).build();
        }
        return insights.toBuilder()
                .predictionConfidence(null)
                .regularityStatus(null)
                .regularityScore(null)
                .regularityLabel(null)
                .regularityReasons(List.of())
                .cycleTrendPoints(List.of())
                .symptomImpactScore(null)
                .phaseSymptomImpacts(List.of())
                .topSymptoms(List.of())
                .advancedAnalyticsAvailable(false)
                .build();
    }

    public String normalizePlan(String rawPlan) {
        if (rawPlan == null || rawPlan.isBlank()) return "FREE";
        String plan = rawPlan.trim().toLowerCase(Locale.ROOT);
        if (plan.contains("year")) return "PREMIUM_YEARLY";
        if (plan.contains("month") || "premium".equals(plan)) return "PREMIUM_MONTHLY";
        return "FREE";
    }

    private Map<String, Boolean> entitlements(boolean premium) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("fullHealthHistoryForAi", true);
        values.put("aiResponseStyles", true);
        values.put("emailReminders", true);
        values.put("customReminderSchedule", true);
        values.put("fertilityWindowReminders", true);
        values.put("healthVideos", true);
        values.put("productRecommendations", true);
        values.put("advancedCycleAnalytics", premium);
        values.put("coupleDailyQuestions", premium);
        values.put("coupleQuestionHistory", premium);
        values.put("coupleConversation", premium);
        values.put("contextualPartnerCare", premium);
        values.put("partnerCareReminders", premium);
        values.put("priorityAi", premium);
        return values;
    }

    public record SubscriptionAccess(
            String tier,
            String plan,
            boolean premium,
            Instant activeUntil,
            boolean cancelAtPeriodEnd,
            int aiDailyLimit,
            Map<String, Boolean> entitlements
    ) {
    }
}
