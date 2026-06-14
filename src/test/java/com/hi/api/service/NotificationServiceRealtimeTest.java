package com.hi.api.service;

import com.hi.api.model.Notification;
import com.hi.api.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceRealtimeTest {

    @Test
    void createNotificationEmitsCreatedEventAndUnreadCount() {
        NotificationRepository repository = mock(NotificationRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        NotificationService service = new NotificationService(
                repository,
                mock(MongoTemplate.class),
                realtimeEventService
        );
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId("notification-1");
            return notification;
        });
        when(repository.countByUserIdAndRead("user-1", false)).thenReturn(1L);

        service.createNotification("user-1", "REMINDER", "Nhắc nhở", "Nội dung");

        verify(realtimeEventService).sendNotification(
                eq("user-1"),
                eq("notification.created"),
                any(Map.class)
        );
        verify(realtimeEventService).sendNotification(
                eq("user-1"),
                eq("notification.unread_count"),
                any(Map.class)
        );
    }
}
