package com.hi.api.service;

import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.User;
import com.hi.api.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminCampaignServiceTest {

    @Test
    void countsPremiumAudienceWithActiveUserFilter() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(4L);
        AdminService service = createService(mongoTemplate, mock(NotificationService.class));

        assertEquals(4L, service.countNotificationAudience("premium"));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(queryCaptor.capture(), eq(User.class));
        String query = queryCaptor.getValue().getQueryObject().toJson();
        assertTrue(query.contains("subscription.plan"));
        assertTrue(query.contains("accountStatus"));
    }

    @Test
    void sendsCampaignAndWritesOneAuditLog() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        NotificationService notificationService = mock(NotificationService.class);
        User recipient = new User();
        recipient.setId("user-1");
        when(mongoTemplate.find(any(Query.class), eq(User.class))).thenReturn(List.of(recipient));

        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminService service = createService(mongoTemplate, notificationService, auditRepository);

        var result = service.sendNotificationCampaign(
                "admin-1",
                "female",
                "Tiêu đề",
                "Nội dung",
                "/notifications",
                "127.0.0.1"
        );

        assertEquals(1, result.get("recipientCount"));
        verify(notificationService).createIdempotentNotification(
                eq("user-1"),
                eq("ADMIN_CAMPAIGN"),
                eq("Tiêu đề"),
                eq("Nội dung"),
                eq("/notifications"),
                anyString(),
                anyMap()
        );
        verify(auditRepository).save(any());
    }

    @Test
    void rejectsUnknownAudience() {
        AdminService service = createService(mock(MongoTemplate.class), mock(NotificationService.class));
        assertThrows(IllegalArgumentException.class, () -> service.countNotificationAudience("unknown"));
    }

    @Test
    void neutralizesSpreadsheetFormulaPrefixes() {
        AdminService service = createService(mock(MongoTemplate.class), mock(NotificationService.class));

        assertEquals("'=1+1",
                ReflectionTestUtils.invokeMethod(service, "escapeCsv", "=1+1"));
        assertEquals("'@SUM(A1:A2)",
                ReflectionTestUtils.invokeMethod(service, "escapeCsv", "@SUM(A1:A2)"));
    }

    @Test
    void hardDeleteRemovesUserAndUnlinksPartner() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminService service = createService(
                userRepository,
                mock(MongoTemplate.class),
                mock(NotificationService.class),
                auditRepository
        );
        User target = new User();
        target.setId("user-1");
        target.setPartnerId("partner-1");
        User partner = new User();
        partner.setId("partner-1");
        partner.setPartnerId("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(target));
        when(userRepository.findById("partner-1")).thenReturn(Optional.of(partner));

        service.hardDeleteUser("admin-1", "user-1", "127.0.0.1");

        verify(userRepository).delete(target);
        verify(userRepository).save(partner);
        assertEquals(null, partner.getPartnerId());
        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertEquals("HARD_DELETE_USER", auditCaptor.getValue().getAction());
        assertEquals("REMOVED", auditCaptor.getValue().getAfterData());
    }

    @Test
    void hardDeleteRejectsSelfDelete() {
        AdminService service = createService(mock(MongoTemplate.class), mock(NotificationService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.hardDeleteUser("admin-1", "admin-1", "127.0.0.1"));
    }

    private AdminService createService(MongoTemplate mongoTemplate, NotificationService notificationService) {
        return createService(mongoTemplate, notificationService, mock(AdminAuditLogRepository.class));
    }

    private AdminService createService(MongoTemplate mongoTemplate,
                                       NotificationService notificationService,
                                       AdminAuditLogRepository auditRepository) {
        return createService(mock(UserRepository.class), mongoTemplate, notificationService, auditRepository);
    }

    private AdminService createService(UserRepository userRepository,
                                       MongoTemplate mongoTemplate,
                                       NotificationService notificationService,
                                       AdminAuditLogRepository auditRepository) {
        return new AdminService(
                userRepository,
                mock(CycleRecordRepository.class),
                mock(DailyLogSymptomRepository.class),
                mock(NotificationRepository.class),
                mock(ChatRepository.class),
                auditRepository,
                mock(TransactionRepository.class),
                mongoTemplate,
                notificationService,
                mock(RealtimeEventService.class),
                mock(AiCostLogRepository.class)
        );
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
