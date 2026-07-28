package com.hi.api.service;

import com.hi.api.dto.request.CreateCycleRecordRequest;
import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.UpdateCycleRecordRequest;
import com.hi.api.exception.ConflictException;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.CycleRecordStatus;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.SymptomSeverity;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CycleRecordService {

    private static final int DEFAULT_CYCLE_LENGTH = 28;
    private static final int DEFAULT_PERIOD_LENGTH = 5;
    private static final int MIN_CYCLE_LENGTH = 10;
    private static final int MAX_CYCLE_LENGTH = 90;
    private static final int MIN_PERIOD_LENGTH = 1;
    private static final int STALE_PERIOD_GAP_DAYS = 2;
    private static final int MAX_PREDICTION_CYCLES = 6;
    private static final String ALGORITHM_VERSION = "cycle-v2";
    private static final int TYPICAL_MIN_CYCLE_LENGTH = 21;
    private static final int TYPICAL_MAX_CYCLE_LENGTH = 35;
    private static final int TYPICAL_MIN_PERIOD_LENGTH = 2;
    private static final int TYPICAL_MAX_PERIOD_LENGTH = 7;

    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final UserRepository userRepository;
    private final SequenceService sequenceService;
    private final RealtimeEventService realtimeEventService;
    private final Clock clock;

    public CycleRecordService(CycleRecordRepository cycleRecordRepository,
                              DailyLogRepository dailyLogRepository,
                              DailyLogSymptomRepository dailyLogSymptomRepository,
                              SymptomDictionaryRepository symptomDictionaryRepository,
                              UserRepository userRepository,
                              SequenceService sequenceService,
                              RealtimeEventService realtimeEventService,
                              Clock clock) {
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.userRepository = userRepository;
        this.sequenceService = sequenceService;
        this.realtimeEventService = realtimeEventService;
        this.clock = clock;
    }

    public List<CycleRecord> getCycleRecords(String userId, LocalDate from, LocalDate to) {
        List<CycleRecord> records;
        if (from != null && to != null) {
            records = cycleRecordRepository.findByUserIdAndStartDateBetweenOrderByStartDateDesc(
                    userId, from.minusDays(MAX_CYCLE_LENGTH), to);
        } else if (from != null) {
            records = cycleRecordRepository.findByUserIdAndStartDateGreaterThanEqualOrderByStartDateDesc(
                    userId, from.minusDays(MAX_CYCLE_LENGTH));
        } else if (to != null) {
            records = cycleRecordRepository.findByUserIdAndStartDateLessThanEqualOrderByStartDateDesc(userId, to);
        } else {
            records = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId);
        }
        if (from != null || to != null) {
            records = records.stream()
                    .filter(record -> {
                        LocalDate start = record.getStartDate();
                        if (start == null) return false;
                        LocalDate end = recordedEndDate(record);
                        boolean overlapsFrom = from == null || !end.isBefore(from);
                        boolean overlapsTo = to == null || !start.isAfter(to);
                        return overlapsFrom && overlapsTo;
                    })
                    .collect(java.util.stream.Collectors.toList());
        }
        return records;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public CycleRecord createCycleRecord(String userId, CreateCycleRecordRequest req) {
        ensureUniqueStartDate(userId, req.getStartDate(), null);
        CycleRecord record = new CycleRecord();
        record.setId(sequenceService.next("cycle_records"));
        record.setUserId(userId);
        CycleRecordStatus requestedStatus = req.getStatus() != null
                ? req.getStatus()
                : req.getEndDate() == null ? CycleRecordStatus.ONGOING : CycleRecordStatus.COMPLETED;
        if (CycleRecordStatus.COMPLETED.equals(requestedStatus) && req.getEndDate() == null) {
            throw new IllegalArgumentException("Kỳ đã hoàn thành phải có ngày kết thúc");
        }
        apply(record, req.getStartDate(), req.getEndDate(), req.getCycleLength(), req.getPeriodLength(),
                req.getNotes(), requestedStatus, req.getIsIgnored());
        if (isActiveStatus(record.getStatus())) {
            findOngoingPeriod(userId, record.getStartDate()).ifPresent(existing -> {
                throw new ConflictException("Kỳ hiện tại chưa kết thúc. Hãy kết thúc kỳ trước khi tạo kỳ mới.");
            });
        }
        ensureNoOverlap(userId, record, null);
        CycleRecord saved;
        try {
            saved = cycleRecordRepository.save(record);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Đã có kỳ đang diễn ra hoặc ngày bắt đầu đã được ghi nhận");
        }
        emitPartnerCycleUpdate(userId, "created", saved);
        return saved;
    }

    public Page<CycleRecord> getCycleRecordHistory(String userId, int page, int limit) {
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId, PageRequest.of(page, limit));
    }

    public CycleRecord getCycleRecord(String userId, Long id) {
        return cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public CycleRecord updateCycleRecord(String userId, Long id, UpdateCycleRecordRequest req) {
        CycleRecord record = cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));

        List<CycleRecord> userCycles = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId);
        boolean isLatest = !userCycles.isEmpty() && userCycles.get(0).getId().equals(id);

        LocalDate effectiveStartDate = req.getStartDate() != null ? req.getStartDate() : record.getStartDate();
        ensureUniqueStartDate(userId, effectiveStartDate, id);

        // Chỉ mở lại kỳ gần nhất khi client yêu cầu rõ ràng. Một field endDate
        // bị bỏ qua trong JSON cũng deserialize thành null nên không được tự ý xóa.
        if (isLatest && isActiveStatus(req.getStatus())) {
            record.setEndDate(null);
            record.setStatus(CycleRecordStatus.ONGOING);
            LocalDate lastBleedingDate = record.getLastBleedingDate() != null
                    ? record.getLastBleedingDate()
                    : record.getStartDate();
            record.setLastBleedingDate(lastBleedingDate);
            record.setPeriodLength(daysInclusive(record.getStartDate(), lastBleedingDate));
        }

        apply(record, req.getStartDate(), req.getEndDate(), req.getCycleLength(), req.getPeriodLength(),
                req.getNotes(), req.getStatus(), req.getIsIgnored());
        ensureNoOverlap(userId, record, id);
        CycleRecord saved = cycleRecordRepository.save(record);
        emitPartnerCycleUpdate(userId, "updated", saved);
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#user.id")
    @Transactional
    public CycleRecord upsertInitialFromProfile(User user) {
        if (user == null || user.getId() == null || user.getLastPeriodDate() == null || user.getLastPeriodDate().isBlank()) {
            return null;
        }
        try {
            LocalDate startDate = LocalDate.parse(user.getLastPeriodDate());
            LocalDate endDate = user.getLastPeriodEndDate() != null && !user.getLastPeriodEndDate().isBlank()
                    ? LocalDate.parse(user.getLastPeriodEndDate())
                    : null;
            validate(startDate, endDate, user.getDefaultCycleLength(), user.getDefaultPeriodLength());
            return cycleRecordRepository.findByUserIdAndStartDate(user.getId(), startDate)
                    .orElseGet(() -> {
                        CycleRecord record = new CycleRecord();
                        record.setId(sequenceService.next("cycle_records"));
                        record.setUserId(user.getId());
                        LocalDate confirmedEnd = endDate != null ? endDate : startDate;
                        apply(record, startDate, confirmedEnd, user.getDefaultCycleLength(),
                                endDate != null ? user.getDefaultPeriodLength() : 1,
                                null, CycleRecordStatus.COMPLETED, false);
                        record.setEndDateEstimated(endDate == null);
                        ensureNoOverlap(user.getId(), record, null);
                        CycleRecord saved = cycleRecordRepository.save(record);
                        emitPartnerCycleUpdate(user.getId(), "created", saved);
                        return saved;
                    });
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Ngày kỳ kinh không hợp lệ");
        }
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public CycleRecord confirmPeriodStart(String userId, LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu là bắt buộc");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        validate(startDate, null, user.getDefaultCycleLength(), 1);

        CycleRecord sameDay = cycleRecordRepository.findByUserIdAndStartDate(userId, startDate).orElse(null);
        if (sameDay != null) {
            if (!CycleRecordStatus.COMPLETED.equals(sameDay.getStatus()) && sameDay.getEndDate() == null) {
                sameDay.setStatus(CycleRecordStatus.ONGOING);
                sameDay.setLastBleedingDate(startDate);
                sameDay.setPeriodLength(1);
                CycleRecord saved = cycleRecordRepository.save(sameDay);
                emitPartnerCycleUpdate(userId, "updated", saved);
                return saved;
            }
            return sameDay;
        }

        Optional<CycleRecord> existingActive = findOngoingPeriod(userId, startDate);
        if (existingActive.isPresent()) {
            CycleRecord existing = existingActive.get();
            LocalDate lastBleeding = recordedEndDate(existing);
            if (!startDate.isAfter(lastBleeding)) {
                throw new ConflictException("Ngày bắt đầu mới phải sau ngày có kinh cuối cùng của kỳ hiện tại.");
            }
            completeAtLastBleeding(existing);
            CycleRecord completed = cycleRecordRepository.save(existing);
            emitPartnerCycleUpdate(userId, "completed", completed);
        }

        CycleRecord record = new CycleRecord();
        record.setId(sequenceService.next("cycle_records"));
        record.setUserId(userId);
        apply(record, startDate, null, user.getDefaultCycleLength(), 1,
                null, CycleRecordStatus.ONGOING, false);
        record.setLastBleedingDate(startDate);
        ensureNoOverlap(userId, record, null);
        CycleRecord saved;
        try {
            saved = cycleRecordRepository.save(record);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Đã có kỳ đang diễn ra hoặc ngày bắt đầu đã được ghi nhận");
        }
        emitPartnerCycleUpdate(userId, "created", saved);
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public CycleRecord syncPeriodFromDailyLog(String userId,
                                              LocalDate logDate,
                                              FlowIntensity flowIntensity,
                                              boolean confirmStart,
                                              boolean confirmEnd) {
        CycleRecord active = confirmStart
                ? confirmPeriodStart(userId, logDate)
                : findOngoingPeriod(userId, logDate).orElse(null);

        if (active == null
                && flowIntensity != null
                && !FlowIntensity.NONE.equals(flowIntensity)) {
            active = findReopenableRecentPeriod(userId, logDate).orElse(null);
            if (active != null) {
                active.setStatus(CycleRecordStatus.ONGOING);
                active.setEndDate(null);
                active.setEndDateEstimated(false);
            }
        }

        if (active == null) {
            if (confirmEnd) {
                throw new IllegalArgumentException("Không có kỳ kinh đang diễn ra để kết thúc");
            }
            return null;
        }
        if (logDate.isBefore(active.getStartDate())) {
            throw new IllegalArgumentException("Ngày ghi nhận không thể trước ngày bắt đầu kỳ kinh");
        }
        if (flowIntensity != null && !FlowIntensity.NONE.equals(flowIntensity)) {
            LocalDate lastBleedingDate = active.getLastBleedingDate();
            if (lastBleedingDate == null || logDate.isAfter(lastBleedingDate)) {
                active.setLastBleedingDate(logDate);
            }
            active.setStatus(CycleRecordStatus.ONGOING);
            active.setEndDate(null);
            active.setPeriodLength(daysInclusive(active.getStartDate(), active.getLastBleedingDate()));
        } else {
            recalculateOngoingPeriodFromLogs(userId, active);
        }

        if (confirmEnd) {
            LocalDate actualEnd = flowIntensity != null && !FlowIntensity.NONE.equals(flowIntensity)
                    ? logDate
                    : active.getLastBleedingDate();
            if (actualEnd == null) {
                actualEnd = active.getStartDate();
            }
            active.setEndDate(actualEnd);
            active.setLastBleedingDate(actualEnd);
            active.setPeriodLength(daysInclusive(active.getStartDate(), actualEnd));
            active.setStatus(CycleRecordStatus.COMPLETED);
        }

        CycleRecord saved = cycleRecordRepository.save(active);
        emitPartnerCycleUpdate(userId, confirmEnd ? "completed" : "updated", saved);
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public void reconcilePeriodAfterLogDeletion(String userId, LocalDate logDate) {
        findRecordContainingOrPreceding(userId, logDate).ifPresent(record -> {
            LocalDate nextStart = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                    .map(CycleRecord::getStartDate)
                    .filter(date -> date != null && date.isAfter(record.getStartDate()))
                    .min(LocalDate::compareTo)
                    .orElse(today().plusDays(1));
            LocalDate upperBound = nextStart.minusDays(1);
            if (upperBound.isAfter(today())) upperBound = today();
            LocalDate lastBleeding = dailyLogRepository
                    .findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, record.getStartDate(), upperBound)
                    .stream()
                    .filter(log -> log.getFlowIntensity() != null && !FlowIntensity.NONE.equals(log.getFlowIntensity()))
                    .map(DailyLog::getLogDate)
                    .max(LocalDate::compareTo)
                    .orElse(record.getStartDate());
            record.setLastBleedingDate(lastBleeding);
            record.setPeriodLength(daysInclusive(record.getStartDate(), lastBleeding));
            if (isActiveStatus(record.getStatus())) {
                record.setEndDate(null);
                record.setStatus(ChronoUnit.DAYS.between(lastBleeding, today()) >= STALE_PERIOD_GAP_DAYS
                        ? CycleRecordStatus.NEEDS_CONFIRMATION
                        : CycleRecordStatus.ONGOING);
            } else {
                record.setEndDate(lastBleeding);
                record.setStatus(CycleRecordStatus.COMPLETED);
            }
            record.setEndDateEstimated(false);
            CycleRecord saved = cycleRecordRepository.save(record);
            emitPartnerCycleUpdate(userId, "updated", saved);
        });
    }

    private Optional<CycleRecord> findRecordContainingOrPreceding(String userId, LocalDate date) {
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .filter(record -> record.getStartDate() != null && !record.getStartDate().isAfter(date))
                .findFirst();
    }

    private void recalculateOngoingPeriodFromLogs(String userId, CycleRecord active) {
        LocalDate today = today();
        LocalDate upperBound = today;
        LocalDate lastBleedingDate = dailyLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, active.getStartDate(), upperBound)
                .stream()
                .filter(log -> log.getFlowIntensity() != null && !FlowIntensity.NONE.equals(log.getFlowIntensity()))
                .map(DailyLog::getLogDate)
                .max(LocalDate::compareTo)
                .orElse(active.getStartDate());
        active.setLastBleedingDate(lastBleedingDate);
        active.setPeriodLength(daysInclusive(active.getStartDate(), lastBleedingDate));
        long noFlowDays = ChronoUnit.DAYS.between(lastBleedingDate, today);
        active.setStatus(noFlowDays >= STALE_PERIOD_GAP_DAYS
                ? CycleRecordStatus.NEEDS_CONFIRMATION
                : CycleRecordStatus.ONGOING);
        active.setEndDate(null);
    }

    private java.util.Optional<CycleRecord> findOngoingPeriod(String userId, LocalDate referenceDate) {
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .filter(record -> record.getStartDate() != null)
                .filter(record -> !record.getStartDate().isAfter(referenceDate))
                .filter(record -> isOngoingRecord(record, referenceDate)
                        || (record.getStatus() == null
                        && record.getEndDate() == null
                        && daysInclusive(record.getStartDate(), referenceDate) <= 90))
                .findFirst();
    }

    private Optional<CycleRecord> findReopenableRecentPeriod(String userId, LocalDate logDate) {
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .filter(record -> CycleRecordStatus.COMPLETED.equals(record.getStatus()))
                .filter(record -> record.getStartDate() != null && !record.getStartDate().isAfter(logDate))
                .filter(record -> {
                    LocalDate recordedEnd = recordedEndDate(record);
                    long gapDays = ChronoUnit.DAYS.between(recordedEnd, logDate);
                    return gapDays >= 1 && gapDays <= STALE_PERIOD_GAP_DAYS;
                })
                .findFirst();
    }

    private boolean isOngoingRecord(CycleRecord record, LocalDate referenceDate) {
        return isActiveStatus(record.getStatus());
    }

    private boolean isActiveStatus(CycleRecordStatus status) {
        return CycleRecordStatus.ONGOING.equals(status)
                || CycleRecordStatus.NEEDS_CONFIRMATION.equals(status);
    }

    private void completeAtLastBleeding(CycleRecord record) {
        LocalDate actualEnd = recordedEndDate(record);
        record.setEndDate(actualEnd);
        record.setLastBleedingDate(actualEnd);
        record.setPeriodLength(daysInclusive(record.getStartDate(), actualEnd));
        record.setStatus(CycleRecordStatus.COMPLETED);
        record.setEndDateEstimated(false);
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public void deleteCycleRecord(String userId, Long id) {
        CycleRecord record = cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));
        cycleRecordRepository.delete(record);
        emitPartnerCycleUpdate(userId, "deleted", record);
    }

    private void emitPartnerCycleUpdate(String userId, String action, CycleRecord record) {
        userRepository.findById(userId)
                .filter(user -> user.getPartnerSharingPreferences() != null
                        && Boolean.TRUE.equals(user.getPartnerSharingPreferences().getShareCycleData()))
                .filter(user -> user.getPartnerId() != null && !user.getPartnerId().isBlank())
                .ifPresent(user -> realtimeEventService.sendPartner(
                        user.getPartnerId(),
                        "partner.cycle.updated",
                        java.util.Map.of(
                                "userId", userId,
                                "action", action,
                                "record", record
                        )
                ));
    }

    public CycleRecordInsightResponse getInsights(String userId) {
        List<CycleRecord> records = cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId);
        User user = userRepository.findById(userId).orElse(null);
        if (records.isEmpty()) {
            return emptyInsights();
        }

        List<CycleRecord> sorted = records.stream()
                .filter(record -> record.getStartDate() != null)
                .sorted(Comparator.comparing(CycleRecord::getStartDate))
                .toList();
        if (sorted.isEmpty()) {
            return emptyInsights();
        }
        LocalDate today = today();
        CycleRecord latest = sorted.get(sorted.size() - 1);
        markNeedsConfirmationIfStale(latest, today);
        boolean periodOngoing = isOngoingRecord(latest, today);
        LocalDate latestBleedingDate = recordedEndDate(latest);
        boolean needsPeriodConfirmation = periodOngoing
                && (CycleRecordStatus.NEEDS_CONFIRMATION.equals(latest.getStatus())
                || ChronoUnit.DAYS.between(latestBleedingDate, today) >= STALE_PERIOD_GAP_DAYS);

        List<Integer> intervals = calculateIntervals(sorted);
        List<Integer> recordedCycleLengths = sorted.stream()
                .map(CycleRecord::getCycleLength)
                .filter(this::isPlausibleCycleLength)
                .toList();
        List<Integer> periodLengths = sorted.stream()
                .filter(record -> !isOngoingRecord(record, today))
                .filter(record -> !Boolean.TRUE.equals(record.getEndDateEstimated()))
                .map(CycleRecord::getPeriodLength)
                .filter(this::isPlausiblePeriodLength)
                .toList();

        List<Integer> predictionIntervals = tail(intervals, MAX_PREDICTION_CYCLES);
        int estimatedCycleLength = robustEstimateOrFallback(
                !predictionIntervals.isEmpty() ? predictionIntervals : tail(recordedCycleLengths, MAX_PREDICTION_CYCLES),
                user != null ? user.getDefaultCycleLength() : null,
                DEFAULT_CYCLE_LENGTH);
        int estimatedPeriodLength = robustEstimateOrFallback(
                tail(periodLengths, MAX_PREDICTION_CYCLES),
                user != null ? user.getDefaultPeriodLength() : null,
                DEFAULT_PERIOD_LENGTH);

        Double averageCycleLength = intervals.isEmpty()
                ? averageOrNull(recordedCycleLengths)
                : averageOrNull(intervals);
        Double averagePeriodLength = averageOrNull(periodLengths);

        LocalDate lastStartDate = latest.getStartDate();
        LocalDate lastBleedingDate = periodOngoing
                ? recordedEndDate(latest)
                : latest.getEndDate() != null
                    ? latest.getEndDate()
                    : recordedEndDate(latest);
        LocalDate lastEndDate = periodOngoing ? null : lastBleedingDate;
        LocalDate estimatedPeriodStartDate = lastStartDate.plusDays(estimatedCycleLength);
        LocalDate estimatedPeriodEndDate = estimatedPeriodStartDate.plusDays(estimatedPeriodLength - 1L);
        List<String> dataQualityIssues = buildDataQualityIssues(sorted, intervals, user, today);
        int uncertaintyDays = predictionUncertaintyDays(predictionIntervals, dataQualityIssues);
        LocalDate predictedStartEarliest = estimatedPeriodStartDate.minusDays(uncertaintyDays);
        LocalDate predictedStartLatest = estimatedPeriodStartDate.plusDays(uncertaintyDays);
        String predictionConfidence = resolvePredictionConfidence(predictionIntervals, dataQualityIssues);
        boolean fertilityEstimateAvailable = isFertilityEstimateAvailable(user, predictionConfidence, dataQualityIssues);
        LocalDate estimatedOvulationDate = fertilityEstimateAvailable
                ? estimatedPeriodStartDate.minusDays(14)
                : null;
        LocalDate fertileWindowStartDate = estimatedOvulationDate != null
                ? estimatedOvulationDate.minusDays(5 + uncertaintyDays)
                : null;
        LocalDate fertileWindowEndDate = estimatedOvulationDate;

        int recordedCycleDay = (int) ChronoUnit.DAYS.between(lastStartDate, today) + 1;
        Integer confirmedPeriodDay = null;
        if (periodOngoing) {
            confirmedPeriodDay = Math.max(1, latest.getPeriodLength() != null
                    ? latest.getPeriodLength()
                    : daysInclusive(lastStartDate, lastBleedingDate));
        } else if (latest.getEndDate() != null
                && !Boolean.TRUE.equals(latest.getEndDateEstimated())
                && !today.isBefore(lastStartDate)
                && !today.isAfter(lastBleedingDate)) {
            confirmedPeriodDay = recordedCycleDay;
        }
        String periodStatus;
        Integer estimatedCycleDay;
        String estimatedPhase;
        LocalDate estimatedCurrentStartDate;
        if (needsPeriodConfirmation) {
            periodStatus = "NEEDS_CONFIRMATION";
            estimatedCurrentStartDate = lastStartDate;
            estimatedCycleDay = recordedCycleDay;
            estimatedPhase = "Cần xác nhận ngày kết thúc";
        } else if (periodOngoing || confirmedPeriodDay != null) {
            periodStatus = "CONFIRMED";
            estimatedCurrentStartDate = lastStartDate;
            estimatedCycleDay = recordedCycleDay;
            estimatedPhase = "Kinh nguyệt";
        } else if (today.isBefore(predictedStartEarliest)) {
            periodStatus = "UPCOMING";
            estimatedCurrentStartDate = lastStartDate;
            estimatedCycleDay = recordedCycleDay;
            estimatedPhase = fertilityEstimateAvailable
                    ? resolvePhase(recordedCycleDay, estimatedPeriodLength, estimatedCycleLength)
                    : "Chưa đủ dữ liệu để ước tính giai đoạn";
        } else if (!today.isAfter(predictedStartLatest)) {
            periodStatus = "PREDICTED";
            estimatedCurrentStartDate = estimatedPeriodStartDate;
            estimatedCycleDay = null;
            estimatedPhase = null;
        } else {
            periodStatus = "DELAYED";
            estimatedCurrentStartDate = estimatedPeriodStartDate;
            estimatedCycleDay = null;
            estimatedPhase = null;
        }
        Integer periodDelayDays = "DELAYED".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(predictedStartLatest, today)
                : 0;
        Integer daysUntilEstimatedPeriod = "UPCOMING".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(today, predictedStartEarliest)
                : null;
        Integer estimatedPeriodDay = "PREDICTED".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(predictedStartEarliest, today) + 1
                : null;
        String fertilityStatus = !fertilityEstimateAvailable
                ? "UNKNOWN"
                : !today.isBefore(fertileWindowStartDate) && !today.isAfter(fertileWindowEndDate)
                    ? "ESTIMATED_WINDOW"
                    : "OUTSIDE_ESTIMATED_WINDOW";

        boolean hasOutliers = intervals.stream().anyMatch(value -> !isTypicalCycleLength(value, user))
                || recordedCycleLengths.stream().anyMatch(value -> !isTypicalCycleLength(value, user))
                || periodLengths.stream().anyMatch(value -> !isTypicalPeriodLength(value));
        List<String> warnings = buildWarnings(hasOutliers, intervals.size(), periodLengths, periodDelayDays, dataQualityIssues);
        if (periodOngoing && confirmedPeriodDay != null && confirmedPeriodDay > TYPICAL_MAX_PERIOD_LENGTH) {
            warnings.add("Kỳ kinh đã được ghi nhận trên 7 ngày. Bạn vẫn có thể tiếp tục theo dõi, nhưng nên trao đổi với bác sĩ nếu tình trạng kéo dài hoặc lượng máu nhiều.");
        }
        appendClinicalLogWarnings(userId, warnings, today.minusDays(90), today);
        RegularityAssessment regularity = assessRegularity(sorted, intervals, periodLengths, hasOutliers, user);
        List<CycleRecordInsightResponse.CycleTrendPoint> trendPoints = buildTrendPoints(sorted, intervals);

        SymptomAnalytics analytics = analyzeSymptoms(
                userId,
                sorted,
                estimatedCycleLength,
                estimatedPeriodLength,
                fertilityEstimateAvailable);
        return CycleRecordInsightResponse.builder()
                .cycleCount(sorted.size())
                .averageCycleLength(averageCycleLength)
                .averagePeriodLength(averagePeriodLength)
                .lastStartDate(lastStartDate)
                .lastRecordedStartDate(lastStartDate)
                .lastRecordedEndDate(lastEndDate)
                .lastBleedingDate(lastBleedingDate)
                .estimatedCurrentCycleStartDate(estimatedCurrentStartDate)
                .estimatedPeriodStartDate(estimatedPeriodStartDate)
                .estimatedPeriodEndDate(estimatedPeriodEndDate)
                .predictedStartEarliest(predictedStartEarliest)
                .predictedStartLatest(predictedStartLatest)
                .estimatedNextStartDate(estimatedPeriodStartDate)
                .estimatedNextEndDate(estimatedPeriodEndDate)
                .estimatedOvulationDate(estimatedOvulationDate)
                .fertileWindowStartDate(fertileWindowStartDate)
                .fertileWindowEndDate(fertileWindowEndDate)
                .currentCycleDay(estimatedCycleDay)
                .currentPhase(estimatedPhase)
                .periodStatus(periodStatus)
                .periodOngoing(periodOngoing)
                .confirmedPeriodDay(confirmedPeriodDay)
                .estimatedCycleDay(estimatedCycleDay)
                .estimatedPhase(estimatedPhase)
                .periodDelayDays(periodDelayDays)
                .daysUntilEstimatedPeriod(daysUntilEstimatedPeriod)
                .estimatedPeriodDay(estimatedPeriodDay)
                .fertilityStatus(fertilityStatus)
                .predictionConfidence(predictionConfidence)
                .predictionBasis(predictionIntervals.isEmpty()
                        ? "Mặc định cá nhân; chưa đủ khoảng chu kỳ hoàn chỉnh"
                        : "Trung vị có trọng số của " + predictionIntervals.size() + " khoảng chu kỳ gần nhất")
                .dataQualityIssues(dataQualityIssues)
                .cycleCompleteness(calculateCompleteness(sorted))
                .fertilityEstimateAvailable(fertilityEstimateAvailable)
                .algorithmVersion(ALGORITHM_VERSION)
                .regularityStatus(regularity.status)
                .regularityScore(regularity.score)
                .regularityLabel(regularity.label)
                .regularityReasons(regularity.reasons)
                .cycleTrendPoints(trendPoints)
                .hasOutliers(hasOutliers)
                .warnings(warnings)
                .symptomImpactScore(analytics.overallImpactScore)
                .phaseSymptomImpacts(analytics.phaseImpacts)
                .topSymptoms(analytics.topSymptoms)
                .advancedAnalyticsAvailable(true)
                .build();
    }

    @Scheduled(cron = "0 10 0 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void markStalePeriodsForConfirmation() {
        LocalDate today = today();
        for (CycleRecord record : cycleRecordRepository.findByStatus(CycleRecordStatus.ONGOING)) {
            markNeedsConfirmationIfStale(record, today);
        }
    }

    private void markNeedsConfirmationIfStale(CycleRecord record, LocalDate referenceDate) {
        if (record == null || !CycleRecordStatus.ONGOING.equals(record.getStatus())) {
            return;
        }
        LocalDate lastBleeding = recordedEndDate(record);
        if (lastBleeding == null
                || ChronoUnit.DAYS.between(lastBleeding, referenceDate) < STALE_PERIOD_GAP_DAYS) {
            return;
        }
        record.setStatus(CycleRecordStatus.NEEDS_CONFIRMATION);
        CycleRecord saved = cycleRecordRepository.save(record);
        emitPartnerCycleUpdate(record.getUserId(), "needs_confirmation", saved);
    }

    private CycleRecordInsightResponse emptyInsights() {
        return CycleRecordInsightResponse.builder()
                .cycleCount(0)
                .fertilityStatus("UNKNOWN")
                .predictionConfidence("LOW")
                .predictionBasis("Chưa có kỳ kinh được xác nhận")
                .dataQualityIssues(List.of("Chưa có dữ liệu chu kỳ"))
                .cycleCompleteness(0)
                .fertilityEstimateAvailable(false)
                .algorithmVersion(ALGORITHM_VERSION)
                .regularityStatus("UNKNOWN")
                .regularityScore(0)
                .regularityLabel("Chưa đủ dữ liệu")
                .regularityReasons(List.of("Cần ít nhất 2 kỳ đã xác nhận để đánh giá xu hướng chu kỳ."))
                .cycleTrendPoints(List.of())
                .hasOutliers(false)
                .warnings(List.of("Chưa đủ dữ liệu để ước tính chu kỳ."))
                .symptomImpactScore(0.0)
                .phaseSymptomImpacts(List.of())
                .topSymptoms(List.of())
                .advancedAnalyticsAvailable(true)
                .build();
    }

    private List<Integer> calculateIntervals(List<CycleRecord> sorted) {
        List<Integer> intervals = new ArrayList<>();
        for (int index = 1; index < sorted.size(); index++) {
            long days = ChronoUnit.DAYS.between(sorted.get(index - 1).getStartDate(), sorted.get(index).getStartDate());
            if (days > 0 && days <= 730) {
                intervals.add((int) days);
            }
        }
        return intervals;
    }

    private RegularityAssessment assessRegularity(List<CycleRecord> sorted,
                                                   List<Integer> intervals,
                                                   List<Integer> periodLengths,
                                                   boolean hasOutliers,
                                                   User user) {
        if (intervals.size() < 2) {
            return new RegularityAssessment(
                    "UNKNOWN",
                    0,
                    "Chưa đủ dữ liệu",
                    List.of("Nên nhập ít nhất 3 kỳ gần nhất để Hi đánh giá xu hướng ổn hơn.")
            );
        }

        int min = intervals.stream().min(Integer::compareTo).orElse(DEFAULT_CYCLE_LENGTH);
        int max = intervals.stream().max(Integer::compareTo).orElse(DEFAULT_CYCLE_LENGTH);
        double avg = intervals.stream().mapToInt(Integer::intValue).average().orElse(DEFAULT_CYCLE_LENGTH);
        int variation = max - min;
        boolean allTypicalCycles = intervals.stream().allMatch(value -> isTypicalCycleLength(value, user));
        boolean allTypicalPeriods = periodLengths.isEmpty() || periodLengths.stream().allMatch(this::isTypicalPeriodLength);
        double mad = medianAbsoluteDeviation(intervals);
        int score = Math.max(0, Math.min(100, (int) Math.round(100 - (mad / Math.max(avg, 1.0)) * 180)));

        List<String> reasons = new ArrayList<>();
        reasons.add("Độ dài chu kỳ dao động khoảng " + variation + " ngày.");
        reasons.add("Chu kỳ trung bình khoảng " + Math.round(avg) + " ngày.");
        if (!allTypicalCycles) reasons.add(isAdolescent(user)
                ? "Có chu kỳ ngoài khoảng tham chiếu thường gặp 21-45 ngày ở tuổi vị thành niên."
                : "Có chu kỳ ngoài khoảng tham chiếu thường gặp 21-35 ngày ở người trưởng thành.");
        if (!allTypicalPeriods) reasons.add("Có kỳ kinh ngoài khoảng tham chiếu 2-7 ngày.");
        if (hasOutliers) reasons.add("Hi vẫn lưu dữ liệu bất thường nhưng giảm ảnh hưởng của outlier khi tính trung bình.");

        if (variation <= 7 && allTypicalCycles && allTypicalPeriods && !hasOutliers) {
            return new RegularityAssessment("REGULAR", Math.max(score, 80), "Mức biến động thấp", reasons);
        }
        if (variation <= 9 && allTypicalCycles && allTypicalPeriods && !hasOutliers) {
            return new RegularityAssessment("NORMAL", Math.max(score, 55), "Mức biến động trung bình", reasons);
        }
        return new RegularityAssessment("IRREGULAR", Math.min(score, 55), "Dữ liệu chu kỳ cần được lưu ý", reasons);
    }

    private List<CycleRecordInsightResponse.CycleTrendPoint> buildTrendPoints(List<CycleRecord> sorted, List<Integer> intervals) {
        List<CycleRecordInsightResponse.CycleTrendPoint> points = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            CycleRecord record = sorted.get(index);
            Integer cycleLength = index == 0
                    ? record.getCycleLength()
                    : (int) ChronoUnit.DAYS.between(sorted.get(index - 1).getStartDate(), record.getStartDate());
            points.add(CycleRecordInsightResponse.CycleTrendPoint.builder()
                    .cycleId(record.getId())
                    .startDate(record.getStartDate())
                    .cycleLength(cycleLength)
                    .periodLength(record.getPeriodLength())
                    .outlier(!isTypicalCycleLength(cycleLength) || !isTypicalPeriodLength(record.getPeriodLength()))
                    .build());
        }
        return points;
    }

    private List<String> buildWarnings(boolean hasOutliers,
                                       int intervalCount,
                                       List<Integer> periodLengths,
                                       Integer periodDelayDays,
                                       List<String> dataQualityIssues) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Các ngày hiển thị là khoảng ước tính, không phải ngày an toàn và không thay thế biện pháp tránh thai hoặc tư vấn y khoa.");
        if (intervalCount == 0) {
            warnings.add("Cần thêm lịch sử chu kỳ để tăng độ chính xác của ước tính.");
        }
        if (hasOutliers) {
            warnings.add("Có dữ liệu ngoài khoảng tham chiếu; hệ thống vẫn giữ dữ liệu và dùng ước lượng bền vững thay vì xóa khỏi lịch sử.");
        }
        if (periodLengths.stream().anyMatch(length -> length > TYPICAL_MAX_PERIOD_LENGTH)) {
            warnings.add("Có kỳ kinh kéo dài trên 7 ngày. Nên trao đổi với cơ sở y tế, đặc biệt nếu lượng máu nhiều hoặc ảnh hưởng sinh hoạt.");
        }
        if (periodDelayDays != null && periodDelayDays >= 90) {
            warnings.add("Bạn chưa ghi nhận kỳ mới trong ít nhất 3 tháng. Nên thử thai nếu có khả năng mang thai và trao đổi với bác sĩ.");
        }
        if (!dataQualityIssues.isEmpty()) {
            warnings.add("Độ tin cậy đã được giảm do dữ liệu thiếu, không đều hoặc có kỳ cần xác nhận.");
        }
        return warnings;
    }

    private void appendClinicalLogWarnings(String userId,
                                           List<String> warnings,
                                           LocalDate from,
                                           LocalDate to) {
        List<DailyLog> recentLogs = dailyLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, from, to);
        if (recentLogs == null) {
            recentLogs = List.of();
        }
        if (recentLogs.stream().anyMatch(log -> FlowIntensity.HEAVY.equals(log.getFlowIntensity())
                && Boolean.TRUE.equals(log.getHasClots()))) {
            warnings.add("Bạn đã ghi nhận lượng kinh nhiều kèm cục máu đông. Nên liên hệ cơ sở y tế nếu phải thay sản phẩm mỗi 1-2 giờ, chóng mặt hoặc ảnh hưởng sinh hoạt.");
        }
        List<Long> logIds = recentLogs.stream().map(DailyLog::getId).filter(java.util.Objects::nonNull).toList();
        if (logIds.isEmpty()) return;
        List<DailyLogSymptom> relations = dailyLogSymptomRepository.findByDailyLogIdIn(logIds);
        if (relations == null || relations.isEmpty()) return;
        Map<Long, SymptomDictionary> dictionaries = symptomDictionaryRepository
                .findAllById(relations.stream().map(DailyLogSymptom::getSymptomId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(SymptomDictionary::getId, item -> item));
        boolean urgent = relations.stream().anyMatch(relation -> {
            SymptomDictionary item = dictionaries.get(relation.getSymptomId());
            if (item == null || item.getName() == null) return false;
            String name = item.getName().toLowerCase(java.util.Locale.ROOT);
            return (SymptomSeverity.SEVERE.equals(relation.getSeverity()) && name.contains("đau"))
                    || name.contains("choáng")
                    || name.contains("ngất")
                    || name.contains("chảy máu giữa kỳ");
        });
        if (urgent) {
            warnings.add("Có dấu hiệu cần lưu ý trong nhật ký gần đây. Nếu đau dữ dội, choáng/ngất hoặc chảy máu bất thường, hãy liên hệ cơ sở y tế; trường hợp nghiêm trọng cần hỗ trợ khẩn cấp.");
        }
    }

    private SymptomAnalytics analyzeSymptoms(String userId,
                                             List<CycleRecord> sortedRecords,
                                             int estimatedCycleLength,
                                             int estimatedPeriodLength,
                                             boolean fertilityEstimateAvailable) {
        LocalDate from = sortedRecords.get(Math.max(0, sortedRecords.size() - 6)).getStartDate();
        List<DailyLog> logs = from == null
                ? dailyLogRepository.findByUserIdOrderByLogDateDesc(userId)
                : dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, from, today());
        if (logs.isEmpty()) {
            return SymptomAnalytics.empty();
        }

        List<Long> logIds = logs.stream().map(DailyLog::getId).toList();
        Map<Long, List<DailyLogSymptom>> symptomByLogId = new HashMap<>();
        for (DailyLogSymptom relation : dailyLogSymptomRepository.findByDailyLogIdIn(logIds)) {
            symptomByLogId.computeIfAbsent(relation.getDailyLogId(), ignored -> new ArrayList<>()).add(relation);
        }

        Map<Long, SymptomDictionary> dictionaryById = new HashMap<>();
        List<Long> symptomIds = symptomByLogId.values().stream()
                .flatMap(List::stream)
                .map(DailyLogSymptom::getSymptomId)
                .distinct()
                .toList();
        for (SymptomDictionary item : symptomDictionaryRepository.findAllById(symptomIds)) {
            dictionaryById.put(item.getId(), item);
        }

        Map<String, Aggregate> phaseAgg = new HashMap<>();
        Map<Long, Aggregate> symptomAgg = new HashMap<>();
        double totalScore = 0.0;

        for (DailyLog log : logs) {
            CycleRecord anchor = findAnchorRecord(sortedRecords, log.getLogDate());
            if (anchor == null) {
                continue;
            }
            int periodLength = anchor.getPeriodLength() != null ? anchor.getPeriodLength() : estimatedPeriodLength;
            int day = (int) Math.max(1, ChronoUnit.DAYS.between(anchor.getStartDate(), log.getLogDate()) + 1);
            String phase = fertilityEstimateAvailable
                    ? resolvePhase(day, periodLength, estimatedCycleLength)
                    : day <= periodLength ? "Kinh nguyệt" : "Ngoài kỳ kinh";

            double flowScore = flowWeight(log.getFlowIntensity());
            double moodScore = moodWeight(log.getMoodScore());
            double logBaseScore = flowScore + moodScore;
            Aggregate phaseAggregate = phaseAgg.computeIfAbsent(phase, ignored -> new Aggregate());
            phaseAggregate.total += logBaseScore;
            phaseAggregate.occurrences += 1;
            totalScore += logBaseScore;

            for (DailyLogSymptom relation : symptomByLogId.getOrDefault(log.getId(), List.of())) {
                double severityScore = severityWeight(relation.getSeverity()) * 2.0;
                double weighted = severityScore + flowScore + moodScore;
                phaseAggregate.total += severityScore;
                totalScore += severityScore;
                Aggregate symptomAggregate = symptomAgg.computeIfAbsent(relation.getSymptomId(), ignored -> new Aggregate());
                symptomAggregate.total += weighted;
                symptomAggregate.severityTotal += severityWeight(relation.getSeverity());
                symptomAggregate.occurrences += 1;
            }
        }

        List<String> phases = fertilityEstimateAvailable
                ? List.of("Kinh nguyệt", "Nang trứng", "Rụng trứng", "Hoàng thể")
                : List.of("Kinh nguyệt", "Ngoài kỳ kinh");
        List<CycleRecordInsightResponse.PhaseSymptomImpact> phaseImpacts = phases
                .stream()
                .map(phase -> {
                    Aggregate agg = phaseAgg.getOrDefault(phase, new Aggregate());
                    double impact = agg.occurrences == 0 ? 0.0 : Math.min(100.0, round2((agg.total / agg.occurrences) * 14.0));
                    return CycleRecordInsightResponse.PhaseSymptomImpact.builder()
                            .phase(phase)
                            .impactScore(impact)
                            .occurrenceCount(agg.occurrences)
                            .build();
                })
                .toList();
        List<CycleRecordInsightResponse.SymptomImpactItem> topSymptoms = symptomAgg.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().total, a.getValue().total))
                .limit(5)
                .map(entry -> {
                    SymptomDictionary dictionary = dictionaryById.get(entry.getKey());
                    Aggregate agg = entry.getValue();
                    double impact = agg.occurrences == 0 ? 0.0 : Math.min(100.0, round2((agg.total / agg.occurrences) * 12.0));
                    double averageSeverity = agg.occurrences == 0 ? 0.0 : round2(agg.severityTotal / agg.occurrences);
                    return CycleRecordInsightResponse.SymptomImpactItem.builder()
                            .symptomId(entry.getKey())
                            .symptomName(dictionary != null ? dictionary.getName() : "Triệu chứng #" + entry.getKey())
                            .impactScore(impact)
                            .averageSeverity(averageSeverity)
                            .occurrenceCount(agg.occurrences)
                            .build();
                })
                .toList();
        double overallImpactScore = Math.min(100.0, round2((totalScore / Math.max(1, logs.size())) * 10.0));
        return new SymptomAnalytics(overallImpactScore, phaseImpacts, topSymptoms);
    }

    private CycleRecord findAnchorRecord(List<CycleRecord> sortedRecords, LocalDate logDate) {
        CycleRecord anchor = null;
        for (CycleRecord record : sortedRecords) {
            if (!record.getStartDate().isAfter(logDate)) {
                anchor = record;
            } else {
                break;
            }
        }
        return anchor;
    }

    private String resolvePhase(int cycleDay, int periodLength, int cycleLength) {
        if (cycleDay <= periodLength) {
            return "Kinh nguyệt";
        }
        int ovulationDay = Math.max(periodLength + 1, cycleLength - 14);
        if (cycleDay < ovulationDay - 1) {
            return "Nang trứng";
        }
        if (cycleDay <= ovulationDay + 1) {
            return "Rụng trứng";
        }
        return "Hoàng thể";
    }

    private void ensureUniqueStartDate(String userId, LocalDate startDate, Long currentId) {
        if (startDate == null) {
            return;
        }
        cycleRecordRepository.findByUserIdAndStartDate(userId, startDate)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Đã có chu kỳ bắt đầu vào ngày này");
                });
    }

    private void ensureNoOverlap(String userId, CycleRecord candidate, Long currentId) {
        LocalDate candidateEnd = effectiveEndDate(candidate);
        cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .filter(existing -> existing.getStartDate() != null)
                .filter(existing -> !candidate.getStartDate().isAfter(effectiveEndDate(existing))
                        && !existing.getStartDate().isAfter(candidateEnd))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ConflictException("Khoảng ngày này đang trùng với một kỳ đã ghi nhận");
                });
    }

    private LocalDate effectiveEndDate(CycleRecord record) {
        return recordedEndDate(record);
    }

    private LocalDate recordedEndDate(CycleRecord record) {
        if (record.getEndDate() != null) {
            return record.getEndDate();
        }
        if (record.getLastBleedingDate() != null) {
            return record.getLastBleedingDate();
        }
        int periodLength = record.getPeriodLength() != null ? record.getPeriodLength() : DEFAULT_PERIOD_LENGTH;
        return record.getStartDate().plusDays(periodLength - 1L);
    }

    private void apply(CycleRecord record, LocalDate startDate, LocalDate endDate, Integer cycleLength,
                       Integer periodLength, String notes, CycleRecordStatus status, Boolean isIgnored) {
        LocalDate effectiveStartDate = startDate != null ? startDate : record.getStartDate();
        LocalDate effectiveEndDate = endDate != null ? endDate : record.getEndDate();
        validate(effectiveStartDate, effectiveEndDate, cycleLength, periodLength);
        if (startDate != null) {
            record.setStartDate(startDate);
        }
        if (endDate != null) {
            record.setEndDate(endDate);
            record.setLastBleedingDate(endDate);
            record.setEndDateEstimated(false);
            record.setStatus(CycleRecordStatus.COMPLETED);
            record.setPeriodLength(daysInclusive(record.getStartDate(), endDate));
        }
        if (cycleLength != null) {
            record.setCycleLength(cycleLength);
        }
        if (periodLength != null) {
            record.setPeriodLength(periodLength);
        }
        if (notes != null) {
            record.setNotes(notes.trim());
        }
        if (status != null) {
            record.setStatus(status);
        }
        if (record.getCycleLength() == null) {
            record.setCycleLength(DEFAULT_CYCLE_LENGTH);
        }
        if (record.getPeriodLength() == null) {
            record.setPeriodLength(isActiveStatus(record.getStatus()) ? 1 : DEFAULT_PERIOD_LENGTH);
        }
        if (isActiveStatus(record.getStatus())) {
            record.setEndDate(null);
            record.setEndDateEstimated(false);
            if (record.getLastBleedingDate() == null) {
                record.setLastBleedingDate(record.getStartDate());
            }
            record.setPeriodLength(daysInclusive(record.getStartDate(), record.getLastBleedingDate()));
        } else if (record.getStatus() == null && record.getEndDate() != null) {
            record.setStatus(CycleRecordStatus.COMPLETED);
        }
        if (CycleRecordStatus.COMPLETED.equals(record.getStatus()) && record.getEndDate() == null) {
            completeAtLastBleeding(record);
        }
        if (isIgnored != null) {
            record.setIsIgnored(isIgnored);
        }
        if (record.getIsIgnored() == null) {
            record.setIsIgnored(false);
        }
    }

    private int daysInclusive(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private void validate(LocalDate startDate, LocalDate endDate, Integer cycleLength, Integer periodLength) {
        if (startDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu là bắt buộc");
        }
        if (startDate.isAfter(today())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được ở tương lai");
        }
        if (endDate != null && endDate.isAfter(today())) {
            throw new IllegalArgumentException("Ngày kết thúc không được ở tương lai");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (!isPlausibleCycleLength(cycleLength) && cycleLength != null) {
            throw new IllegalArgumentException("Độ dài chu kỳ phải từ 10 đến 90 ngày");
        }
        if (!isPlausiblePeriodLength(periodLength) && periodLength != null) {
            throw new IllegalArgumentException("Độ dài kỳ kinh phải từ 1 ngày trở lên");
        }
    }

    private int robustEstimateOrFallback(List<Integer> values, Integer fallback, int defaultValue) {
        if (values.isEmpty()) {
            return fallback != null ? fallback : defaultValue;
        }
        List<WeightedValue> weighted = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            weighted.add(new WeightedValue(values.get(index), index + 1));
        }
        weighted.sort(Comparator.comparingInt(WeightedValue::value));
        int totalWeight = weighted.stream().mapToInt(WeightedValue::weight).sum();
        int cumulative = 0;
        for (WeightedValue item : weighted) {
            cumulative += item.weight();
            if (cumulative * 2 >= totalWeight) {
                return Math.max(1, item.value());
            }
        }
        return Math.max(1, values.get(values.size() - 1));
    }

    private List<Integer> tail(List<Integer> values, int limit) {
        if (values.size() <= limit) return values;
        return values.subList(values.size() - limit, values.size());
    }

    private double median(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        List<Integer> sortedValues = values.stream().sorted().toList();
        int middle = sortedValues.size() / 2;
        return sortedValues.size() % 2 == 0
                ? (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0
                : sortedValues.get(middle);
    }

    private double medianAbsoluteDeviation(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        double center = median(values);
        List<Integer> deviations = values.stream()
                .map(value -> (int) Math.round(Math.abs(value - center)))
                .toList();
        return median(deviations);
    }

    private int predictionUncertaintyDays(List<Integer> intervals, List<String> dataQualityIssues) {
        if (intervals.size() < 3) return 7;
        int uncertainty = Math.max(2, (int) Math.ceil(1.4826 * medianAbsoluteDeviation(intervals)));
        if (!dataQualityIssues.isEmpty()) uncertainty = Math.max(uncertainty, 7);
        return Math.min(14, uncertainty);
    }

    private String resolvePredictionConfidence(List<Integer> intervals, List<String> dataQualityIssues) {
        double mad = medianAbsoluteDeviation(intervals);
        if (intervals.size() >= 6 && mad <= 2.0 && dataQualityIssues.isEmpty()) return "HIGH";
        if (intervals.size() >= 3 && mad <= 5.0
                && dataQualityIssues.stream().noneMatch(issue -> issue.contains("bối cảnh"))) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildDataQualityIssues(List<CycleRecord> records,
                                                List<Integer> intervals,
                                                User user,
                                                LocalDate today) {
        List<String> issues = new ArrayList<>();
        if (intervals.size() < 3) issues.add("Cần ít nhất 4 ngày bắt đầu kỳ để tạo 3 khoảng chu kỳ");
        if (Boolean.TRUE.equals(user != null ? user.getIrregularCycle() : null)) {
            issues.add("Người dùng đã khai báo chu kỳ không đều");
        }
        if (records.stream().anyMatch(record -> isActiveStatus(record.getStatus())
                && ChronoUnit.DAYS.between(recordedEndDate(record), today) >= STALE_PERIOD_GAP_DAYS)) {
            issues.add("Có kỳ đang diễn ra cần xác nhận ngày kết thúc");
        }
        if (records.stream().anyMatch(record -> Boolean.TRUE.equals(record.getEndDateEstimated()))) {
            issues.add("Có ngày kết thúc được suy ra từ dữ liệu ban đầu");
        }
        if (intervals.stream().anyMatch(interval -> interval < MIN_CYCLE_LENGTH || interval > MAX_CYCLE_LENGTH)) {
            issues.add("Có khoảng chu kỳ dưới 10 hoặc trên 90 ngày; có thể thiếu lần ghi nhận");
        }
        if (intervals.stream().anyMatch(interval -> !isTypicalCycleLength(interval, user))) {
            issues.add("Có khoảng chu kỳ ngoài khoảng tham chiếu theo độ tuổi");
        }
        if (records.get(records.size() - 1).getStartDate().isBefore(today.minusDays(90))) {
            issues.add("Lần ghi nhận kỳ gần nhất đã quá 90 ngày");
        }
        if (hasFertilitySuppressingContext(user)) {
            issues.add("Có bối cảnh sức khỏe làm dự đoán theo lịch không phù hợp");
        }
        return issues.stream().distinct().toList();
    }

    private int calculateCompleteness(List<CycleRecord> records) {
        if (records.isEmpty()) return 0;
        long complete = records.stream()
                .filter(record -> CycleRecordStatus.COMPLETED.equals(record.getStatus()))
                .filter(record -> record.getEndDate() != null)
                .filter(record -> !Boolean.TRUE.equals(record.getEndDateEstimated()))
                .count();
        return (int) Math.round((complete * 100.0) / records.size());
    }

    private boolean isFertilityEstimateAvailable(User user,
                                                 String predictionConfidence,
                                                 List<String> dataQualityIssues) {
        return !"LOW".equals(predictionConfidence)
                && !Boolean.TRUE.equals(user != null ? user.getIrregularCycle() : null)
                && !hasFertilitySuppressingContext(user)
                && dataQualityIssues.stream().noneMatch(issue ->
                        issue.contains("trên 90 ngày")
                                || issue.contains("ngoài khoảng tham chiếu")
                                || issue.contains("cần xác nhận"));
    }

    private boolean hasFertilitySuppressingContext(User user) {
        return user != null && (Boolean.TRUE.equals(user.getPregnant())
                || Boolean.TRUE.equals(user.getPostpartum())
                || Boolean.TRUE.equals(user.getBreastfeeding())
                || Boolean.TRUE.equals(user.getHormonalContraception())
                || Boolean.TRUE.equals(user.getPerimenopause()));
    }

    private boolean isAdolescent(User user) {
        if (user == null || user.getBirthDate() == null || user.getBirthDate().isBlank()) return false;
        try {
            return Period.between(LocalDate.parse(user.getBirthDate()), today()).getYears() < 18;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private Double averageOrNull(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        return round2(values.stream().mapToInt(Integer::intValue).average().orElse(0.0));
    }

    private boolean isPlausibleCycleLength(Integer value) {
        return value != null && value >= MIN_CYCLE_LENGTH && value <= MAX_CYCLE_LENGTH;
    }

    private boolean isTypicalCycleLength(Integer value) {
        return value != null && value >= TYPICAL_MIN_CYCLE_LENGTH && value <= TYPICAL_MAX_CYCLE_LENGTH;
    }

    private boolean isTypicalCycleLength(Integer value, User user) {
        int max = isAdolescent(user) ? 45 : TYPICAL_MAX_CYCLE_LENGTH;
        return value != null && value >= TYPICAL_MIN_CYCLE_LENGTH && value <= max;
    }

    private boolean isPlausiblePeriodLength(Integer value) {
        return value != null && value >= MIN_PERIOD_LENGTH;
    }

    private boolean isTypicalPeriodLength(Integer value) {
        return value != null && value >= TYPICAL_MIN_PERIOD_LENGTH && value <= TYPICAL_MAX_PERIOD_LENGTH;
    }

    private double severityWeight(SymptomSeverity severity) {
        if (severity == null) {
            return 1.0;
        }
        return switch (severity) {
            case MILD -> 1.0;
            case MODERATE -> 2.0;
            case SEVERE -> 3.0;
        };
    }

    private double flowWeight(FlowIntensity flowIntensity) {
        if (flowIntensity == null) {
            return 0.0;
        }
        return switch (flowIntensity) {
            case NONE -> 0.0;
            case LIGHT -> 1.0;
            case MEDIUM -> 2.0;
            case HEAVY -> 3.0;
        };
    }

    private double moodWeight(Integer moodScore) {
        if (moodScore == null) {
            return 0.0;
        }
        return switch (Math.max(1, Math.min(5, moodScore))) {
            case 1 -> 1.6;
            case 2 -> 1.0;
            case 3 -> 0.5;
            default -> 0.0;
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Aggregate {
        private double total;
        private double severityTotal;
        private long occurrences;
    }

    private record SymptomAnalytics(
            double overallImpactScore,
            List<CycleRecordInsightResponse.PhaseSymptomImpact> phaseImpacts,
            List<CycleRecordInsightResponse.SymptomImpactItem> topSymptoms
    ) {
        private static SymptomAnalytics empty() {
            return new SymptomAnalytics(0.0, List.of(), List.of());
        }
    }

    private record RegularityAssessment(String status, int score, String label, List<String> reasons) {}

    private record WeightedValue(int value, int weight) {}
}
