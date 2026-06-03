package com.hi.api.service;

import com.hi.api.dto.request.UpsertDailyLogRequest;
import com.hi.api.model.DailyLog;
import com.hi.api.model.FlowIntensity;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyLogServiceTest {

    private DailyLogRepository dailyLogRepository;
    private CycleRecordService cycleRecordService;
    private DailyLogService service;

    @BeforeEach
    void setUp() {
        dailyLogRepository = mock(DailyLogRepository.class);
        DailyLogSymptomRepository dailyLogSymptomRepository = mock(DailyLogSymptomRepository.class);
        SymptomDictionaryRepository symptomDictionaryRepository = mock(SymptomDictionaryRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        cycleRecordService = mock(CycleRecordService.class);
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        service = new DailyLogService(
                dailyLogRepository,
                dailyLogSymptomRepository,
                symptomDictionaryRepository,
                sequenceService,
                cycleRecordService,
                userRepository,
                notificationService
        );
    }

    @Test
    void lightFlowWithoutConfirmationDoesNotCreateCycle() {
        LocalDate today = LocalDate.now();
        when(dailyLogRepository.findByUserIdAndLogDate("female-1", today)).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpsertDailyLogRequest request = new UpsertDailyLogRequest();
        request.setFlowIntensity(FlowIntensity.LIGHT);

        service.upsertLog("female-1", today, request);

        verify(cycleRecordService, never()).confirmPeriodStart(any(), any());
    }

    @Test
    void lightFlowWithConfirmationCreatesCycle() {
        LocalDate today = LocalDate.now();
        when(dailyLogRepository.findByUserIdAndLogDate("female-1", today)).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpsertDailyLogRequest request = new UpsertDailyLogRequest();
        request.setFlowIntensity(FlowIntensity.LIGHT);
        request.setConfirmPeriodStart(true);

        service.upsertLog("female-1", today, request);

        verify(cycleRecordService).confirmPeriodStart("female-1", today);
    }

    @Test
    void confirmationWithoutFlowIsRejected() {
        UpsertDailyLogRequest request = new UpsertDailyLogRequest();
        request.setFlowIntensity(FlowIntensity.NONE);
        request.setConfirmPeriodStart(true);

        assertThrows(IllegalArgumentException.class, () -> service.upsertLog("female-1", LocalDate.now(), request));
        verify(dailyLogRepository, never()).save(any());
        verify(cycleRecordService, never()).confirmPeriodStart(any(), any());
    }

    @Test
    void clotsArePersistedWithDailyLog() {
        LocalDate today = LocalDate.now();
        when(dailyLogRepository.findByUserIdAndLogDate("female-1", today)).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpsertDailyLogRequest request = new UpsertDailyLogRequest();
        request.setHasClots(true);

        DailyLog saved = service.upsertLog("female-1", today, request);

        assertTrue(saved.getHasClots());
    }
}
