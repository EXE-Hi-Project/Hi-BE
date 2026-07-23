package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReminderServiceDailyTipEmailTest {

    private NotificationService notificationService;
    private EmailService emailService;
    private ReminderService service;
    private User user;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        emailService = mock(EmailService.class);
        service = new ReminderService(
                mock(UserRepository.class),
                mock(CycleRecordService.class),
                notificationService,
                emailService,
                mock(DailyLogRepository.class),
                mock(ChatBoxAIService.class),
                mock(ChatContextService.class),
                mock(SubscriptionAccessService.class)
        );

        user = new User();
        user.setId("user-1");
        user.setName("Nguyễn An");
        user.setEmail("an@example.com");
        user.setNotificationPreferences(new User.NotificationPreferences());
    }

    @Test
    void dailyTipStillCreatesInAppNotificationButEmailIsOffByDefault() {
        ReflectionTestUtils.invokeMethod(service, "createDailyCheckIn", user, LocalDate.of(2026, 7, 23));

        verify(notificationService).createIdempotentNotification(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()
        );
        verify(emailService, never()).sendDailyCheckInEmail(anyString(), anyString(), anyString());
    }

    @Test
    void dailyTipEmailRequiresDedicatedOptIn() {
        user.getNotificationPreferences().setDailyHealthTipsEmailEnabled(true);

        ReflectionTestUtils.invokeMethod(service, "createDailyCheckIn", user, LocalDate.of(2026, 7, 23));

        verify(emailService).sendDailyCheckInEmail(eq("an@example.com"), eq("Nguyễn An"), anyString());
    }

    @Test
    void globalEmailChannelStillOverridesDedicatedOptIn() {
        user.getNotificationPreferences().setDailyHealthTipsEmailEnabled(true);
        user.getNotificationPreferences().setEmailEnabled(false);

        ReflectionTestUtils.invokeMethod(service, "createDailyCheckIn", user, LocalDate.of(2026, 7, 23));

        verify(emailService, never()).sendDailyCheckInEmail(anyString(), anyString(), anyString());
    }
}
