package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AdminServiceTest {

    private final SubscriptionAccessService subscriptionAccessService =
            new SubscriptionAccessService(mock(UserRepository.class));

    private final AdminService service = new AdminService(
            mock(UserRepository.class),
            mock(CycleRecordRepository.class),
            mock(DailyLogSymptomRepository.class),
            mock(NotificationRepository.class),
            mock(ChatRepository.class),
            mock(AdminAuditLogRepository.class),
            mock(TransactionRepository.class),
            mock(MongoTemplate.class),
            mock(NotificationService.class),
            mock(RealtimeEventService.class),
            mock(AiCostLogRepository.class),
            subscriptionAccessService
    );

    @Test
    void subscriptionStatsCountOnlyDirectActivePlans() {
        User free = user("free", null);
        User monthly = user("monthly", null);
        monthly.setSubscription(subscription("premium_monthly", "active", Instant.now().plusSeconds(3600)));
        User yearlyCanceledButValid = user("yearly", null);
        yearlyCanceledButValid.setSubscription(subscription("premium_yearly", "canceled", Instant.now().plusSeconds(3600)));
        User expired = user("expired", null);
        expired.setSubscription(subscription("premium_monthly", "active", Instant.now().minusSeconds(60)));

        Map<String, Object> stats = ReflectionTestUtils.invokeMethod(
                service,
                "buildSubscriptionStats",
                List.of(free, monthly, yearlyCanceledButValid, expired)
        );

        assertEquals(2L, stats.get("free"));
        assertEquals(1L, stats.get("hiPro"));
        assertEquals(1L, stats.get("hiMax"));
        assertEquals(2L, stats.get("activePaidTotal"));
    }

    @Test
    void coupleStatsRequireMutualLinksAndDoNotDoubleCount() {
        User first = user("a", "b");
        User second = user("b", "a");
        User oneWay = user("c", "missing");
        User unpaired = user("d", null);

        Map<String, Object> stats = ReflectionTestUtils.invokeMethod(
                service,
                "buildCoupleStats",
                List.of(first, second, oneWay, unpaired)
        );

        assertEquals(4L, stats.get("eligibleUsers"));
        assertEquals(2L, stats.get("pairedUsers"));
        assertEquals(1L, stats.get("pairedCouples"));
        assertEquals(2L, stats.get("unpairedUsers"));
        assertEquals(50D, stats.get("pairingRatePct"));
    }

    private User user(String id, String partnerId) {
        User user = new User();
        user.setId(id);
        user.setPartnerId(partnerId);
        user.setRole("user");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private User.SubscriptionInfo subscription(String plan, String status, Instant currentPeriodEnd) {
        User.SubscriptionInfo subscription = new User.SubscriptionInfo();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        return subscription;
    }
}
