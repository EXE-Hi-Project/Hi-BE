package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionAccessServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubscriptionAccessService service = new SubscriptionAccessService(userRepository);

    @Test
    void mapsLegacyPlansAndKeepsCanceledAccessUntilPeriodEnd() {
        User monthly = user("monthly-user", "premium", "active", Instant.now().plusSeconds(3_600));
        User yearly = user("yearly-user", "yearly", "canceled", Instant.now().plusSeconds(3_600));

        assertThat(service.getAccess(monthly).plan()).isEqualTo("PREMIUM_MONTHLY");
        assertThat(service.getAccess(monthly).aiDailyLimit()).isEqualTo(50);
        assertThat(service.getAccess(yearly).plan()).isEqualTo("PREMIUM_YEARLY");
        assertThat(service.getAccess(yearly).premium()).isTrue();
    }

    @Test
    void expiredPremiumFallsBackToFreeQuota() {
        User user = user("expired-user", "premium_monthly", "active", Instant.now().minusSeconds(60));

        SubscriptionAccessService.SubscriptionAccess access = service.getAccess(user);

        assertThat(access.tier()).isEqualTo("FREE");
        assertThat(access.plan()).isEqualTo("FREE");
        assertThat(access.aiDailyLimit()).isEqualTo(5);
    }

    @Test
    void partnerPremiumUnlocksOnlyCoupleEntitlementsForFreeUser() {
        User freeUser = user("free-user", "free", null, null);
        User premiumPartner = user("premium-partner", "premium_yearly", "active", Instant.now().plusSeconds(3_600));
        freeUser.setPartnerId(premiumPartner.getId());
        premiumPartner.setPartnerId(freeUser.getId());
        when(userRepository.findById(premiumPartner.getId())).thenReturn(Optional.of(premiumPartner));

        assertThat(service.hasPremiumForCouple(freeUser)).isTrue();
        assertThat(service.getEffectiveEntitlements(freeUser))
                .containsEntry("coupleDailyQuestions", true)
                .containsEntry("contextualPartnerCare", true)
                .containsEntry("advancedCycleAnalytics", false)
                .containsEntry("priorityAi", false);
    }

    @Test
    void freeInsightsKeepSafetyWarningsButHideAdvancedAnalytics() {
        CycleRecordInsightResponse insights = CycleRecordInsightResponse.builder()
                .regularityScore(82)
                .regularityLabel("Ổn định")
                .cycleTrendPoints(List.of())
                .hasOutliers(true)
                .warnings(List.of("Chu kỳ gần nhất khác biệt đáng kể."))
                .symptomImpactScore(35.0)
                .build();

        CycleRecordInsightResponse filtered = service.filterInsights(insights, false);

        assertThat(filtered.isAdvancedAnalyticsAvailable()).isFalse();
        assertThat(filtered.getRegularityScore()).isNull();
        assertThat(filtered.getSymptomImpactScore()).isNull();
        assertThat(filtered.isHasOutliers()).isTrue();
        assertThat(filtered.getWarnings()).containsExactly("Chu kỳ gần nhất khác biệt đáng kể.");
    }

    private User user(String id, String plan, String status, Instant periodEnd) {
        User user = new User();
        user.setId(id);
        User.SubscriptionInfo subscription = new User.SubscriptionInfo();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodEnd(periodEnd);
        user.setSubscription(subscription);
        return user;
    }
}
