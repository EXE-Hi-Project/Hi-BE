package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.exception.ConflictException;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CycleRecordServiceTest {

    private CycleRecordRepository cycleRecordRepository;
    private DailyLogRepository dailyLogRepository;
    private UserRepository userRepository;
    private CycleRecordService service;

    @BeforeEach
    void setUp() {
        cycleRecordRepository = mock(CycleRecordRepository.class);
        dailyLogRepository = mock(DailyLogRepository.class);
        DailyLogSymptomRepository dailyLogSymptomRepository = mock(DailyLogSymptomRepository.class);
        SymptomDictionaryRepository symptomDictionaryRepository = mock(SymptomDictionaryRepository.class);
        userRepository = mock(UserRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        service = new CycleRecordService(
                cycleRecordRepository,
                dailyLogRepository,
                dailyLogSymptomRepository,
                symptomDictionaryRepository,
                userRepository,
                sequenceService
        );
    }

    @Test
    void getInsightsShowsPredictedWindowWithoutCreatingCycle() {
        String userId = "female-1";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(29), 28, 5);
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("PREDICTED", insights.getPeriodStatus());
        assertEquals(LocalDate.now().minusDays(1), insights.getEstimatedPeriodStartDate());
        assertEquals(2, insights.getEstimatedCycleDay());
        assertEquals(2, insights.getEstimatedPeriodDay());
        assertEquals(null, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getConfirmedPeriodDay());
        assertEquals("LOW", insights.getPredictionConfidence());
        assertFalse(insights.getWarnings().isEmpty());
        verify(cycleRecordRepository, never()).save(any());
    }

    @Test
    void getInsightsShowsDelayWithoutRollingPredictionForward() {
        String userId = "female-delayed";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(40), 28, 5);
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("DELAYED", insights.getPeriodStatus());
        assertEquals(LocalDate.now().minusDays(12), insights.getEstimatedPeriodStartDate());
        assertEquals(12, insights.getPeriodDelayDays());
        assertEquals(null, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getEstimatedPeriodDay());
        assertEquals(null, insights.getConfirmedPeriodDay());
        verify(cycleRecordRepository, never()).save(any());
    }

    @Test
    void getInsightsReturnsCountdownBeforeEstimatedPeriod() {
        String userId = "female-upcoming";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(25), 28, 5);
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("UPCOMING", insights.getPeriodStatus());
        assertEquals(3, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getEstimatedPeriodDay());
    }

    @Test
    void getInsightsReturnsConfirmedPeriodDayWithoutPredictionCounters() {
        String userId = "female-confirmed";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(1), 28, 5);
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("CONFIRMED", insights.getPeriodStatus());
        assertEquals(2, insights.getConfirmedPeriodDay());
        assertEquals(null, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getEstimatedPeriodDay());
    }

    @Test
    void upsertInitialFromProfileDoesNotCreateDuplicate() {
        User user = new User();
        user.setId("female-2");
        user.setLastPeriodDate("2026-05-01");
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);
        CycleRecord existing = cycleRecord(user.getId(), LocalDate.parse(user.getLastPeriodDate()), 28, 5);

        when(cycleRecordRepository.findByUserIdAndStartDate(user.getId(), existing.getStartDate()))
                .thenReturn(Optional.of(existing));

        CycleRecord result = service.upsertInitialFromProfile(user);

        assertEquals(existing, result);
        verify(cycleRecordRepository, never()).save(any());
    }

    @Test
    void createCycleRejectsDuplicateDate() {
        LocalDate date = LocalDate.now().minusDays(2);
        when(cycleRecordRepository.findByUserIdAndStartDate("female-3", date))
                .thenReturn(Optional.of(cycleRecord("female-3", date, 28, 5)));
        com.hi.api.dto.request.CreateCycleRecordRequest request = new com.hi.api.dto.request.CreateCycleRecordRequest();
        request.setStartDate(date);

        assertThrows(ConflictException.class, () -> service.createCycleRecord("female-3", request));
    }

    @Test
    void createCycleRejectsFutureEndDate() {
        LocalDate startDate = LocalDate.now().minusDays(2);
        com.hi.api.dto.request.CreateCycleRecordRequest request = new com.hi.api.dto.request.CreateCycleRecordRequest();
        request.setStartDate(startDate);
        request.setEndDate(LocalDate.now().plusDays(1));

        when(cycleRecordRepository.findByUserIdAndStartDate("female-future-end", startDate))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createCycleRecord("female-future-end", request));
    }

    @Test
    void confirmPeriodStartDoesNotCreateDuplicate() {
        String userId = "female-confirm";
        LocalDate today = LocalDate.now();
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);
        CycleRecord existing = cycleRecord(userId, today, 28, 5);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cycleRecordRepository.findByUserIdAndStartDate(userId, today)).thenReturn(Optional.of(existing));

        CycleRecord result = service.confirmPeriodStart(userId, today);

        assertEquals(existing, result);
        verify(cycleRecordRepository, never()).save(any());
    }

    private CycleRecord cycleRecord(String userId, LocalDate startDate, int cycleLength, int periodLength) {
        CycleRecord record = new CycleRecord();
        record.setId(1L);
        record.setUserId(userId);
        record.setStartDate(startDate);
        record.setCycleLength(cycleLength);
        record.setPeriodLength(periodLength);
        record.setIsIgnored(false);
        return record;
    }
}
