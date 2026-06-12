package com.hi.api.service;

import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServicePaginationTest {

    @Test
    void scheduledJobsUseBoundedActiveUserPages() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findActiveUsers(any(Pageable.class))).thenReturn(Page.empty());
        when(userRepository.findActiveFemaleUsers(any(Pageable.class))).thenReturn(Page.empty());

        ReminderService service = new ReminderService(
                userRepository,
                mock(CycleRecordService.class),
                mock(NotificationService.class),
                mock(EmailService.class),
                mock(DailyLogRepository.class),
                mock(ChatBoxAIService.class),
                mock(ChatContextService.class),
                mock(SubscriptionAccessService.class)
        );

        service.generateDailyReminders();
        service.generateSymptomLogReminders();

        verify(userRepository).findActiveUsers(any(Pageable.class));
        verify(userRepository).findActiveFemaleUsers(any(Pageable.class));
        verify(userRepository, never()).findAll();
    }
}
