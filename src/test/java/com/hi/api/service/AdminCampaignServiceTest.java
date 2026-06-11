package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

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

    private AdminService createService(MongoTemplate mongoTemplate, NotificationService notificationService) {
        return createService(mongoTemplate, notificationService, mock(AdminAuditLogRepository.class));
    }

    private AdminService createService(MongoTemplate mongoTemplate,
                                       NotificationService notificationService,
                                       AdminAuditLogRepository auditRepository) {
        return new AdminService(
                mock(UserRepository.class),
                mock(CycleRecordRepository.class),
                mock(DailyLogSymptomRepository.class),
                mock(NotificationRepository.class),
                mock(ChatRepository.class),
                auditRepository,
                mock(TransactionRepository.class),
                mongoTemplate,
                notificationService
        );
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
