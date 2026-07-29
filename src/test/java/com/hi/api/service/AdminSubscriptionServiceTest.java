package com.hi.api.service;

import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.User;
import com.hi.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminSubscriptionServiceTest {

    private UserRepository userRepository;
    private AdminAuditLogRepository auditLogRepository;
    private RealtimeEventService realtimeEventService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditLogRepository = mock(AdminAuditLogRepository.class);
        realtimeEventService = mock(RealtimeEventService.class);
        service = new AdminService(
                userRepository,
                mock(CycleRecordRepository.class),
                mock(DailyLogSymptomRepository.class),
                mock(NotificationRepository.class),
                mock(ChatRepository.class),
                auditLogRepository,
                mock(TransactionRepository.class),
                mock(MongoTemplate.class),
                mock(NotificationService.class),
                realtimeEventService,
                mock(AiCostLogRepository.class),
                mock(SubscriptionAccessService.class)
        );
    }

    @Test
    void grantsPremiumAndNotifiesTheUser() {
        User target = user("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        Instant before = Instant.now();
        User result = service.updateUserSubscription(
                "admin-1", "user-1", "premium", 30, "Chăm sóc khách hàng", "127.0.0.1"
        );

        assertSame(target, result);
        assertEquals("PREMIUM_MONTHLY", target.getSubscription().getPlan());
        assertEquals("active", target.getSubscription().getStatus());
        assertFalse(target.getSubscription().getCancelAtPeriodEnd());
        assertTrue(target.getSubscription().getCurrentPeriodEnd().isAfter(before.plusSeconds(29L * 24 * 3600)));
        verify(realtimeEventService).sendSubscription(
                eq("user-1"), eq("subscription.updated"), any(Map.class)
        );
        verify(realtimeEventService).sendAdminOverviewUpdated(
                eq("admin.overview.updated"), any(Map.class)
        );

        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals("UPDATE_USER_SUBSCRIPTION", audit.getValue().getAction());
        assertEquals("admin-1", audit.getValue().getActorUserId());
        assertEquals("user-1", audit.getValue().getTargetUserId());
    }

    @Test
    void downgradesToFreeAndClearsPaidPeriod() {
        User target = user("user-1");
        target.getSubscription().setPlan("PREMIUM_YEARLY");
        target.getSubscription().setStatus("active");
        target.getSubscription().setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        target.getSubscription().setPayosOrderCode(123L);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        service.updateUserSubscription("admin-1", "user-1", "free", 30, null, "127.0.0.1");

        assertEquals("free", target.getSubscription().getPlan());
        assertEquals("inactive", target.getSubscription().getStatus());
        assertNull(target.getSubscription().getCurrentPeriodEnd());
        assertNull(target.getSubscription().getPayosOrderCode());
    }

    @Test
    void rejectsUnknownPlanWithoutChangingTheUser() {
        assertThrows(IllegalArgumentException.class, () ->
                service.updateUserSubscription(
                        "admin-1", "user-1", "enterprise", 30, null, "127.0.0.1"
                )
        );
        verifyNoInteractions(userRepository);
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        user.setRole("user");
        user.setAccountStatus("ACTIVE");
        user.setSubscription(new User.SubscriptionInfo());
        return user;
    }
}
