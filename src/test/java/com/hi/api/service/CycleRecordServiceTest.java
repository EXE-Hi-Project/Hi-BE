package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.exception.ConflictException;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.CycleRecordStatus;
import com.hi.api.model.DailyLog;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Clock;
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
    private SequenceService sequenceService;
    private CycleRecordService service;

    @BeforeEach
    void setUp() {
        cycleRecordRepository = mock(CycleRecordRepository.class);
        dailyLogRepository = mock(DailyLogRepository.class);
        DailyLogSymptomRepository dailyLogSymptomRepository = mock(DailyLogSymptomRepository.class);
        SymptomDictionaryRepository symptomDictionaryRepository = mock(SymptomDictionaryRepository.class);
        userRepository = mock(UserRepository.class);
        sequenceService = mock(SequenceService.class);
        service = new CycleRecordService(
                cycleRecordRepository,
                dailyLogRepository,
                dailyLogSymptomRepository,
                symptomDictionaryRepository,
                userRepository,
                sequenceService,
                mock(RealtimeEventService.class),
                Clock.systemDefaultZone()
        );
    }

    @Test
    void getInsightsKeepsDateBeforeCentralEstimateAsUpcoming() {
        String userId = "female-1";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(21), 28, 5);
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
        assertEquals(LocalDate.now().plusDays(7), insights.getEstimatedPeriodStartDate());
        assertEquals(22, insights.getEstimatedCycleDay());
        assertEquals(null, insights.getEstimatedPeriodDay());
        assertEquals(0, insights.getPeriodDelayDays());
        assertEquals(7, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getConfirmedPeriodDay());
        assertEquals("LOW", insights.getPredictionConfidence());
        assertFalse(insights.getWarnings().isEmpty());
        verify(cycleRecordRepository, never()).save(any());
    }

    @Test
    void getInsightsCountsEstimatedPeriodDayFromCentralEstimate() {
        String userId = "female-predicted";
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
        assertEquals(2, insights.getEstimatedPeriodDay());
        assertEquals(null, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getConfirmedPeriodDay());
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
        assertEquals(5, insights.getPeriodDelayDays());
        assertEquals(null, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getEstimatedPeriodDay());
        assertEquals(null, insights.getConfirmedPeriodDay());
        verify(cycleRecordRepository, never()).save(any());
    }

    @Test
    void getInsightsReturnsCountdownBeforeEstimatedPeriod() {
        String userId = "female-upcoming";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(15), 28, 5);
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
        assertEquals(13, insights.getDaysUntilEstimatedPeriod());
        assertEquals(null, insights.getEstimatedPeriodDay());
        assertEquals("UNKNOWN", insights.getFertilityStatus());
        assertFalse(insights.isFertilityEstimateAvailable());
    }

    @Test
    void getInsightsDoesNotEstimateFertilityFromOneCycle() {
        String userId = "female-fertile";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(13), 28, 5);
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

        assertEquals("UNKNOWN", insights.getFertilityStatus());
        assertEquals(null, insights.getEstimatedOvulationDate());
        assertFalse(insights.isFertilityEstimateAvailable());
    }

    @Test
    void getInsightsEstimatesFertilityOnlyWithStableHistory() {
        String userId = "female-fertile-stable";
        LocalDate latestStart = LocalDate.now().minusDays(13);
        List<CycleRecord> records = new java.util.ArrayList<>();
        for (int index = 0; index < 7; index++) {
            CycleRecord record = cycleRecord(userId, latestStart.minusDays(index * 28L), 28, 5);
            record.setId((long) index + 1);
            record.setEndDate(record.getStartDate().plusDays(4));
            record.setLastBleedingDate(record.getEndDate());
            record.setStatus(CycleRecordStatus.COMPLETED);
            record.setEndDateEstimated(false);
            records.add(record);
        }
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(records);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("ESTIMATED_WINDOW", insights.getFertilityStatus());
        assertEquals("HIGH", insights.getPredictionConfidence());
        assertEquals(LocalDate.now().plusDays(1), insights.getEstimatedOvulationDate());
        assertTrue(insights.isFertilityEstimateAvailable());
    }

    @Test
    void getInsightsReturnsConfirmedPeriodDayWithoutPredictionCounters() {
        String userId = "female-confirmed";
        CycleRecord record = cycleRecord(userId, LocalDate.now().minusDays(1), 28, 5);
        record.setEndDate(LocalDate.now());
        record.setLastBleedingDate(LocalDate.now());
        record.setStatus(CycleRecordStatus.COMPLETED);
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
    void createCycleRejectsOverlappingPeriodRange() {
        String userId = "female-overlap";
        CycleRecord existing = cycleRecord(userId, LocalDate.now().minusDays(10), 28, 5);
        existing.setEndDate(LocalDate.now().minusDays(6));
        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId)).thenReturn(List.of(existing));

        com.hi.api.dto.request.CreateCycleRecordRequest request = new com.hi.api.dto.request.CreateCycleRecordRequest();
        request.setStartDate(LocalDate.now().minusDays(8));
        request.setEndDate(LocalDate.now().minusDays(4));

        assertThrows(ConflictException.class, () -> service.createCycleRecord(userId, request));
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
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CycleRecord result = service.confirmPeriodStart(userId, today);

        assertEquals(existing, result);
        assertEquals(CycleRecordStatus.ONGOING, result.getStatus());
        assertEquals(1, result.getPeriodLength());
    }

    @Test
    void dailyFlowExtendsOngoingPeriodBeyondFiveDays() {
        String userId = "female-day-six";
        LocalDate startDate = LocalDate.now().minusDays(5);
        CycleRecord active = cycleRecord(userId, startDate, 28, 5);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(startDate.plusDays(4));

        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId)).thenReturn(List.of(active));
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CycleRecord result = service.syncPeriodFromDailyLog(
                userId,
                LocalDate.now(),
                FlowIntensity.LIGHT,
                false,
                false
        );

        assertEquals(CycleRecordStatus.ONGOING, result.getStatus());
        assertEquals(LocalDate.now(), result.getLastBleedingDate());
        assertEquals(6, result.getPeriodLength());
        assertEquals(null, result.getEndDate());
    }

    @Test
    void dailyFlowOnDaySixReopensLegacyFiveDayRecordInsteadOfStartingAnotherCycle() {
        String userId = "female-legacy-day-six";
        LocalDate startDate = LocalDate.now().minusDays(5);
        CycleRecord completed = cycleRecord(userId, startDate, 28, 5);
        completed.setStatus(CycleRecordStatus.COMPLETED);
        completed.setEndDate(startDate.plusDays(4));
        completed.setLastBleedingDate(completed.getEndDate());

        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId))
                .thenReturn(List.of(completed));
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CycleRecord result = service.syncPeriodFromDailyLog(
                userId,
                LocalDate.now(),
                FlowIntensity.LIGHT,
                false,
                false
        );

        assertEquals(CycleRecordStatus.ONGOING, result.getStatus());
        assertEquals(LocalDate.now(), result.getLastBleedingDate());
        assertEquals(6, result.getPeriodLength());
        assertEquals(null, result.getEndDate());
    }

    @Test
    void endingWithoutFlowUsesLastRecordedBleedingDay() {
        String userId = "female-finish";
        LocalDate startDate = LocalDate.now().minusDays(6);
        LocalDate lastBleedingDate = LocalDate.now().minusDays(1);
        CycleRecord active = cycleRecord(userId, startDate, 28, 6);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(lastBleedingDate);
        DailyLog bleedingLog = new DailyLog();
        bleedingLog.setLogDate(lastBleedingDate);
        bleedingLog.setFlowIntensity(FlowIntensity.LIGHT);

        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId)).thenReturn(List.of(active));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of(bleedingLog));
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CycleRecord result = service.syncPeriodFromDailyLog(
                userId,
                LocalDate.now(),
                FlowIntensity.NONE,
                false,
                true
        );

        assertEquals(CycleRecordStatus.COMPLETED, result.getStatus());
        assertEquals(lastBleedingDate, result.getEndDate());
        assertEquals(6, result.getPeriodLength());
    }

    @Test
    void ongoingPeriodIsConfirmedButExcludedFromAveragePeriodLength() {
        String userId = "female-ongoing";
        LocalDate startDate = LocalDate.now().minusDays(5);
        CycleRecord active = cycleRecord(userId, startDate, 28, 6);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(LocalDate.now());
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(active));
        when(cycleRecordRepository.save(active)).thenReturn(active);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("CONFIRMED", insights.getPeriodStatus());
        assertTrue(insights.isPeriodOngoing());
        assertEquals(6, insights.getConfirmedPeriodDay());
        assertEquals(null, insights.getAveragePeriodLength());
    }

    @Test
    void staleOngoingPeriodRequiresConfirmationButRemainsEditable() {
        String userId = "female-stale";
        CycleRecord active = cycleRecord(userId, LocalDate.now().minusDays(10), 28, 8);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(LocalDate.now().minusDays(3));
        User user = new User();
        user.setId(userId);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(List.of(active));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals("NEEDS_CONFIRMATION", insights.getPeriodStatus());
        assertEquals(CycleRecordStatus.NEEDS_CONFIRMATION, active.getStatus());
        assertTrue(insights.isPeriodOngoing());
        assertTrue(insights.getDataQualityIssues().stream().anyMatch(issue -> issue.contains("xác nhận")));
        verify(cycleRecordRepository).save(active);
    }

    @Test
    void scheduledMaintenanceMarksStaleOngoingPeriodsForConfirmation() {
        String userId = "female-scheduled-stale";
        CycleRecord active = cycleRecord(userId, LocalDate.now().minusDays(8), 28, 5);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(LocalDate.now().minusDays(3));

        when(cycleRecordRepository.findByStatus(CycleRecordStatus.ONGOING)).thenReturn(List.of(active));
        when(cycleRecordRepository.save(active)).thenReturn(active);

        service.markStalePeriodsForConfirmation();

        assertEquals(CycleRecordStatus.NEEDS_CONFIRMATION, active.getStatus());
        verify(cycleRecordRepository).save(active);
    }

    @Test
    void continuousBleedingBeyondThirtyDaysIsRecordedAndWarnedInsteadOfRejected() {
        String userId = "female-long-bleeding";
        LocalDate startDate = LocalDate.now().minusDays(30);
        CycleRecord active = cycleRecord(userId, startDate, 28, 30);
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setLastBleedingDate(LocalDate.now().minusDays(1));

        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId)).thenReturn(List.of(active));
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CycleRecord result = service.syncPeriodFromDailyLog(
                userId, LocalDate.now(), FlowIntensity.HEAVY, false, false);

        assertEquals(31, result.getPeriodLength());
        assertEquals(LocalDate.now(), result.getLastBleedingDate());
        assertEquals(CycleRecordStatus.ONGOING, result.getStatus());
    }

    @Test
    void confirmingNewStartClosesForgottenActivePeriodAtLastBleedingDay() {
        String userId = "female-new-after-stale";
        LocalDate today = LocalDate.now();
        CycleRecord old = cycleRecord(userId, today.minusDays(35), 28, 6);
        old.setStatus(CycleRecordStatus.NEEDS_CONFIRMATION);
        old.setLastBleedingDate(today.minusDays(30));
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cycleRecordRepository.findByUserIdAndStartDate(userId, today)).thenReturn(Optional.empty());
        when(cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId)).thenReturn(List.of(old));
        when(cycleRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sequenceService.next("cycle_records")).thenReturn(99L);

        CycleRecord created = service.confirmPeriodStart(userId, today);

        assertEquals(CycleRecordStatus.COMPLETED, old.getStatus());
        assertEquals(today.minusDays(30), old.getEndDate());
        assertEquals(CycleRecordStatus.ONGOING, created.getStatus());
        assertEquals(today, created.getStartDate());
    }

    @Test
    void irregularContextSuppressesCalendarFertilityEstimate() {
        String userId = "female-irregular-context";
        LocalDate latestStart = LocalDate.now().minusDays(13);
        List<CycleRecord> records = new java.util.ArrayList<>();
        for (int index = 0; index < 7; index++) {
            CycleRecord record = cycleRecord(userId, latestStart.minusDays(index * 28L), 28, 5);
            record.setId((long) index + 1);
            record.setEndDate(record.getStartDate().plusDays(4));
            record.setStatus(CycleRecordStatus.COMPLETED);
            records.add(record);
        }
        User user = new User();
        user.setId(userId);
        user.setIrregularCycle(true);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(records);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertFalse(insights.isFertilityEstimateAvailable());
        assertEquals("UNKNOWN", insights.getFertilityStatus());
        assertEquals(null, insights.getEstimatedOvulationDate());
    }

    @Test
    void predictionIgnoresIntervalThatLooksLikeMissedTracking() {
        String userId = "female-missed-tracking";
        LocalDate latestStart = LocalDate.now().minusDays(10);
        List<LocalDate> starts = List.of(
                latestStart.minusDays(168),
                latestStart.minusDays(140),
                latestStart.minusDays(112),
                latestStart.minusDays(56),
                latestStart.minusDays(28),
                latestStart
        );
        List<CycleRecord> records = new java.util.ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            CycleRecord record = cycleRecord(userId, starts.get(index), 28, 5);
            record.setId((long) index + 1);
            record.setEndDate(record.getStartDate().plusDays(4));
            record.setLastBleedingDate(record.getEndDate());
            record.setStatus(CycleRecordStatus.COMPLETED);
            record.setEndDateEstimated(false);
            records.add(record);
        }
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(records);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertEquals(latestStart.plusDays(28), insights.getEstimatedPeriodStartDate());
        assertEquals(1, insights.getSuspectedMissedCycleCount());
        assertTrue(insights.getDataQualityIssues().stream().anyMatch(issue -> issue.contains("bỏ sót")));
        assertEquals("cycle-v3", insights.getAlgorithmVersion());
    }

    @Test
    void predictionReturnsCalibratedRangesAndPeriodLengthRange() {
        String userId = "female-calibrated-range";
        LocalDate latestStart = LocalDate.now().minusDays(12);
        List<Integer> intervals = List.of(28, 29, 27, 30, 28, 29, 28);
        List<LocalDate> starts = new java.util.ArrayList<>();
        LocalDate start = latestStart;
        starts.add(start);
        for (int index = intervals.size() - 1; index >= 0; index--) {
            start = start.minusDays(intervals.get(index));
            starts.add(start);
        }
        List<CycleRecord> records = new java.util.ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            CycleRecord record = cycleRecord(userId, starts.get(index), 28, 4 + index % 3);
            record.setId((long) index + 1);
            record.setEndDate(record.getStartDate().plusDays(record.getPeriodLength() - 1L));
            record.setLastBleedingDate(record.getEndDate());
            record.setStatus(CycleRecordStatus.COMPLETED);
            record.setEndDateEstimated(false);
            records.add(record);
        }
        User user = new User();
        user.setId(userId);
        user.setDefaultCycleLength(28);
        user.setDefaultPeriodLength(5);

        when(cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId))
                .thenReturn(records);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(any(), any(), any()))
                .thenReturn(List.of());

        CycleRecordInsightResponse insights = service.getInsights(userId);

        assertTrue(insights.getPredictionInterval50Days() >= 2);
        assertTrue(insights.getPredictionInterval80Days() > insights.getPredictionInterval50Days());
        assertEquals(insights.getEstimatedPeriodStartDate().minusDays(insights.getPredictionInterval50Days()),
                insights.getPredictionRange50Start());
        assertEquals(insights.getEstimatedPeriodStartDate().plusDays(insights.getPredictionInterval80Days()),
                insights.getPredictionRange80End());
        assertTrue(insights.getEstimatedPeriodLengthMin() <= insights.getEstimatedPeriodLengthMax());
        assertTrue(insights.getPredictionModel() != null && !insights.getPredictionModel().isBlank());
        assertTrue(insights.getPredictionErrorMedianDays() != null);
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
