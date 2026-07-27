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
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CycleRecordService {

    private static final int DEFAULT_CYCLE_LENGTH = 28;
    private static final int DEFAULT_PERIOD_LENGTH = 5;
    private static final int MIN_CYCLE_LENGTH = 10;
    private static final int MAX_CYCLE_LENGTH = 90;
    private static final int MIN_PERIOD_LENGTH = 1;
    private static final int MAX_PERIOD_LENGTH = 30;
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

    public CycleRecordService(CycleRecordRepository cycleRecordRepository,
                              DailyLogRepository dailyLogRepository,
                              DailyLogSymptomRepository dailyLogSymptomRepository,
                              SymptomDictionaryRepository symptomDictionaryRepository,
                              UserRepository userRepository,
                              SequenceService sequenceService,
                              RealtimeEventService realtimeEventService) {
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.userRepository = userRepository;
        this.sequenceService = sequenceService;
        this.realtimeEventService = realtimeEventService;
    }

    public List<CycleRecord> getCycleRecords(String userId, LocalDate from, LocalDate to) {
        List<CycleRecord> records = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId);
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
    public CycleRecord createCycleRecord(String userId, CreateCycleRecordRequest req) {
        ensureUniqueStartDate(userId, req.getStartDate(), null);
        CycleRecord record = new CycleRecord();
        record.setId(sequenceService.next("cycle_records"));
        record.setUserId(userId);
        CycleRecordStatus requestedStatus = req.getStatus() != null
                ? req.getStatus()
                : req.getEndDate() == null ? CycleRecordStatus.ONGOING : CycleRecordStatus.COMPLETED;
        apply(record, req.getStartDate(), req.getEndDate(), req.getCycleLength(), req.getPeriodLength(),
                req.getNotes(), requestedStatus, req.getIsIgnored());
        if (CycleRecordStatus.ONGOING.equals(record.getStatus())) {
            findOngoingPeriod(userId, record.getStartDate()).ifPresent(existing -> {
                throw new ConflictException("Kỳ hiện tại chưa kết thúc. Hãy kết thúc kỳ trước khi tạo kỳ mới.");
            });
        }
        ensureNoOverlap(userId, record, null);
        CycleRecord saved = cycleRecordRepository.save(record);
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
    public CycleRecord updateCycleRecord(String userId, Long id, UpdateCycleRecordRequest req) {
        CycleRecord record = cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));

        List<CycleRecord> userCycles = cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId);
        boolean isLatest = !userCycles.isEmpty() && userCycles.get(0).getId().equals(id);

        LocalDate effectiveStartDate = req.getStartDate() != null ? req.getStartDate() : record.getStartDate();
        ensureUniqueStartDate(userId, effectiveStartDate, id);

        // Chỉ mở lại kỳ gần nhất khi client yêu cầu rõ ràng. Một field endDate
        // bị bỏ qua trong JSON cũng deserialize thành null nên không được tự ý xóa.
        if (isLatest && CycleRecordStatus.ONGOING.equals(req.getStatus())) {
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
                        apply(record, startDate, endDate, user.getDefaultCycleLength(), user.getDefaultPeriodLength(),
                                null, CycleRecordStatus.COMPLETED, false);
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

        findOngoingPeriod(userId, startDate).ifPresent(existing -> {
            throw new ConflictException("Kỳ hiện tại chưa kết thúc. Hãy ghi nhận tiếp hoặc kết thúc kỳ trước khi bắt đầu kỳ mới.");
        });

        CycleRecord record = new CycleRecord();
        record.setId(sequenceService.next("cycle_records"));
        record.setUserId(userId);
        apply(record, startDate, null, user.getDefaultCycleLength(), 1,
                null, CycleRecordStatus.ONGOING, false);
        record.setLastBleedingDate(startDate);
        ensureNoOverlap(userId, record, null);
        CycleRecord saved = cycleRecordRepository.save(record);
        emitPartnerCycleUpdate(userId, "created", saved);
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public CycleRecord syncPeriodFromDailyLog(String userId,
                                              LocalDate logDate,
                                              FlowIntensity flowIntensity,
                                              boolean confirmStart,
                                              boolean confirmEnd) {
        CycleRecord active = confirmStart
                ? confirmPeriodStart(userId, logDate)
                : findOngoingPeriod(userId, logDate).orElse(null);

        if (active == null) {
            if (confirmEnd) {
                throw new IllegalArgumentException("Không có kỳ kinh đang diễn ra để kết thúc");
            }
            return null;
        }
        if (logDate.isBefore(active.getStartDate())) {
            throw new IllegalArgumentException("Ngày ghi nhận không thể trước ngày bắt đầu kỳ kinh");
        }
        if (daysInclusive(active.getStartDate(), logDate) > MAX_PERIOD_LENGTH
                && flowIntensity != null
                && !FlowIntensity.NONE.equals(flowIntensity)) {
            throw new IllegalArgumentException("Một kỳ kinh không thể dài hơn 30 ngày");
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
    public void reconcilePeriodAfterLogDeletion(String userId, LocalDate logDate) {
        findOngoingPeriod(userId, logDate).ifPresent(active -> {
            recalculateOngoingPeriodFromLogs(userId, active);
            CycleRecord saved = cycleRecordRepository.save(active);
            emitPartnerCycleUpdate(userId, "updated", saved);
        });
    }

    private void recalculateOngoingPeriodFromLogs(String userId, CycleRecord active) {
        LocalDate today = LocalDate.now();
        LocalDate upperBound = active.getStartDate().plusDays(MAX_PERIOD_LENGTH - 1L);
        if (upperBound.isAfter(today)) {
            upperBound = today;
        }
        LocalDate lastBleedingDate = dailyLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, active.getStartDate(), upperBound)
                .stream()
                .filter(log -> log.getFlowIntensity() != null && !FlowIntensity.NONE.equals(log.getFlowIntensity()))
                .map(DailyLog::getLogDate)
                .max(LocalDate::compareTo)
                .orElse(active.getStartDate());
        active.setLastBleedingDate(lastBleedingDate);
        active.setPeriodLength(daysInclusive(active.getStartDate(), lastBleedingDate));
        active.setStatus(CycleRecordStatus.ONGOING);
        active.setEndDate(null);
    }

    private java.util.Optional<CycleRecord> findOngoingPeriod(String userId, LocalDate referenceDate) {
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .filter(record -> record.getStartDate() != null)
                .filter(record -> !record.getStartDate().isAfter(referenceDate))
                .filter(record -> isOngoingRecord(record, referenceDate)
                        || (record.getStatus() == null
                        && record.getEndDate() == null
                        && daysInclusive(record.getStartDate(), referenceDate) <= MAX_PERIOD_LENGTH))
                .findFirst();
    }

    private boolean isOngoingRecord(CycleRecord record, LocalDate referenceDate) {
        return CycleRecordStatus.ONGOING.equals(record.getStatus());
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
        LocalDate today = LocalDate.now();
        CycleRecord latest = sorted.get(sorted.size() - 1);
        boolean periodOngoing = isOngoingRecord(latest, today);

        List<Integer> intervals = calculateIntervals(sorted);
        List<Integer> typicalIntervals = intervals.stream().filter(this::isTypicalCycleLength).toList();
        List<Integer> recordedCycleLengths = sorted.stream()
                .map(CycleRecord::getCycleLength)
                .filter(this::isPlausibleCycleLength)
                .toList();
        List<Integer> typicalRecordedCycleLengths = recordedCycleLengths.stream().filter(this::isTypicalCycleLength).toList();
        List<Integer> periodLengths = sorted.stream()
                .filter(record -> !isOngoingRecord(record, today))
                .map(CycleRecord::getPeriodLength)
                .filter(this::isPlausiblePeriodLength)
                .toList();
        List<Integer> typicalPeriodLengths = periodLengths.stream().filter(this::isTypicalPeriodLength).toList();

        int estimatedCycleLength = roundedAverageOrFallback(
                !typicalIntervals.isEmpty() ? typicalIntervals : typicalRecordedCycleLengths,
                user != null ? user.getDefaultCycleLength() : null,
                DEFAULT_CYCLE_LENGTH);
        int estimatedPeriodLength = roundedAverageOrFallback(
                !typicalPeriodLengths.isEmpty() ? typicalPeriodLengths : periodLengths,
                user != null ? user.getDefaultPeriodLength() : null,
                DEFAULT_PERIOD_LENGTH);

        Double averageCycleLength = intervals.isEmpty()
                ? averageOrNull(!typicalRecordedCycleLengths.isEmpty() ? typicalRecordedCycleLengths : recordedCycleLengths)
                : averageOrNull(!typicalIntervals.isEmpty() ? typicalIntervals : intervals);
        Double averagePeriodLength = averageOrNull(!typicalPeriodLengths.isEmpty() ? typicalPeriodLengths : periodLengths);

        LocalDate lastStartDate = latest.getStartDate();
        LocalDate lastBleedingDate = periodOngoing
                ? recordedEndDate(latest)
                : latest.getEndDate() != null
                    ? latest.getEndDate()
                    : recordedEndDate(latest);
        LocalDate lastEndDate = periodOngoing ? null : lastBleedingDate;
        LocalDate estimatedPeriodStartDate = lastStartDate.plusDays(estimatedCycleLength);
        LocalDate estimatedPeriodEndDate = estimatedPeriodStartDate.plusDays(estimatedPeriodLength - 1L);
        LocalDate estimatedOvulationDate = estimatedPeriodStartDate.minusDays(14);
        LocalDate fertileWindowStartDate = estimatedOvulationDate.minusDays(5);
        LocalDate fertileWindowEndDate = estimatedOvulationDate.plusDays(1);

        int recordedCycleDay = (int) ChronoUnit.DAYS.between(lastStartDate, today) + 1;
        Integer confirmedPeriodDay = null;
        if (periodOngoing) {
            confirmedPeriodDay = Math.max(1, latest.getPeriodLength() != null
                    ? latest.getPeriodLength()
                    : daysInclusive(lastStartDate, lastBleedingDate));
        } else if (!today.isBefore(lastStartDate) && !today.isAfter(lastBleedingDate)) {
            confirmedPeriodDay = recordedCycleDay;
        }
        String periodStatus;
        Integer estimatedCycleDay;
        String estimatedPhase;
        LocalDate estimatedCurrentStartDate;
        if (periodOngoing || confirmedPeriodDay != null) {
            periodStatus = "CONFIRMED";
            estimatedCurrentStartDate = lastStartDate;
            estimatedCycleDay = recordedCycleDay;
            estimatedPhase = "Kinh nguyệt";
        } else if (today.isBefore(estimatedPeriodStartDate)) {
            periodStatus = "UPCOMING";
            estimatedCurrentStartDate = lastStartDate;
            estimatedCycleDay = recordedCycleDay;
            estimatedPhase = resolvePhase(recordedCycleDay, estimatedPeriodLength, estimatedCycleLength);
        } else {
            periodStatus = "DELAYED";
            estimatedCurrentStartDate = estimatedPeriodStartDate;
            estimatedCycleDay = null;
            estimatedPhase = null;
        }
        Integer periodDelayDays = "DELAYED".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(estimatedPeriodStartDate, today)
                : 0;
        Integer daysUntilEstimatedPeriod = "UPCOMING".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(today, estimatedPeriodStartDate)
                : null;
        Integer estimatedPeriodDay = "PREDICTED".equals(periodStatus)
                ? (int) ChronoUnit.DAYS.between(estimatedPeriodStartDate, today) + 1
                : null;
        String fertilityStatus = !today.isBefore(fertileWindowStartDate) && !today.isAfter(fertileWindowEndDate)
                ? "HIGH"
                : "LOW";

        boolean hasOutliers = intervals.stream().anyMatch(value -> !isTypicalCycleLength(value))
                || recordedCycleLengths.stream().anyMatch(value -> !isTypicalCycleLength(value))
                || periodLengths.stream().anyMatch(value -> !isTypicalPeriodLength(value));
        List<String> warnings = buildWarnings(hasOutliers, intervals.size());
        if (periodOngoing && confirmedPeriodDay != null && confirmedPeriodDay > TYPICAL_MAX_PERIOD_LENGTH) {
            warnings.add("Kỳ kinh đã được ghi nhận trên 7 ngày. Bạn vẫn có thể tiếp tục theo dõi, nhưng nên trao đổi với bác sĩ nếu tình trạng kéo dài hoặc lượng máu nhiều.");
        }
        String predictionConfidence = typicalIntervals.size() >= 3 ? "HIGH" : typicalIntervals.isEmpty() ? "LOW" : "MEDIUM";
        RegularityAssessment regularity = assessRegularity(sorted, intervals, periodLengths, hasOutliers);
        List<CycleRecordInsightResponse.CycleTrendPoint> trendPoints = buildTrendPoints(sorted, intervals);

        SymptomAnalytics analytics = analyzeSymptoms(userId, sorted, estimatedCycleLength, estimatedPeriodLength);
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

    private CycleRecordInsightResponse emptyInsights() {
        return CycleRecordInsightResponse.builder()
                .cycleCount(0)
                .fertilityStatus("UNKNOWN")
                .predictionConfidence("LOW")
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
            if (days >= MIN_CYCLE_LENGTH && days <= MAX_CYCLE_LENGTH) {
                intervals.add((int) days);
            }
        }
        return intervals;
    }

    private RegularityAssessment assessRegularity(List<CycleRecord> sorted,
                                                  List<Integer> intervals,
                                                  List<Integer> periodLengths,
                                                  boolean hasOutliers) {
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
        boolean allTypicalCycles = intervals.stream().allMatch(this::isTypicalCycleLength);
        boolean allTypicalPeriods = periodLengths.isEmpty() || periodLengths.stream().allMatch(this::isTypicalPeriodLength);
        int score = Math.max(0, Math.min(100, (int) Math.round(100 - (variation / Math.max(avg, 1.0)) * 100)));

        List<String> reasons = new ArrayList<>();
        reasons.add("Độ dài chu kỳ dao động khoảng " + variation + " ngày.");
        reasons.add("Chu kỳ trung bình khoảng " + Math.round(avg) + " ngày.");
        if (!allTypicalCycles) reasons.add("Có chu kỳ ngoài khoảng tham chiếu 21-35 ngày.");
        if (!allTypicalPeriods) reasons.add("Có kỳ kinh ngoài khoảng tham chiếu 2-7 ngày.");
        if (hasOutliers) reasons.add("Hi vẫn lưu dữ liệu bất thường nhưng giảm ảnh hưởng của outlier khi tính trung bình.");

        if (variation <= 7 && allTypicalCycles && allTypicalPeriods && !hasOutliers) {
            return new RegularityAssessment("REGULAR", Math.max(score, 80), "Chu kỳ khá đều", reasons);
        }
        if (variation <= 12 && allTypicalPeriods) {
            return new RegularityAssessment("NORMAL", Math.max(score, 55), "Chu kỳ trong mức bình thường", reasons);
        }
        return new RegularityAssessment("IRREGULAR", Math.min(score, 55), "Chu kỳ có dấu hiệu bất thường", reasons);
    }

    private List<CycleRecordInsightResponse.CycleTrendPoint> buildTrendPoints(List<CycleRecord> sorted, List<Integer> intervals) {
        List<CycleRecordInsightResponse.CycleTrendPoint> points = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            CycleRecord record = sorted.get(index);
            Integer cycleLength = index == 0
                    ? record.getCycleLength()
                    : intervals.size() >= index ? intervals.get(index - 1) : record.getCycleLength();
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

    private List<String> buildWarnings(boolean hasOutliers, int intervalCount) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Các ngày hiển thị là ước tính, không thay thế biện pháp tránh thai hoặc tư vấn y khoa.");
        if (intervalCount == 0) {
            warnings.add("Cần thêm lịch sử chu kỳ để tăng độ chính xác của ước tính.");
        }
        if (hasOutliers) {
            warnings.add("Có dữ liệu ngoài khoảng tham chiếu; hệ thống vẫn lưu để bạn theo dõi và đã hạn chế ảnh hưởng lên trung bình.");
        }
        return warnings;
    }

    private SymptomAnalytics analyzeSymptoms(String userId,
                                             List<CycleRecord> sortedRecords,
                                             int estimatedCycleLength,
                                             int estimatedPeriodLength) {
        LocalDate from = sortedRecords.get(Math.max(0, sortedRecords.size() - 6)).getStartDate();
        List<DailyLog> logs = dailyLogRepository.findByUserIdOrderByLogDateDesc(userId);
        if (from != null) {
            LocalDate today = LocalDate.now();
            logs = logs.stream()
                    .filter(log -> {
                        LocalDate d = log.getLogDate();
                        if (d == null) return false;
                        return !d.isBefore(from) && !d.isAfter(today);
                    })
                    .collect(java.util.stream.Collectors.toList());
        }
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
            int normalizedDay = ((day - 1) % estimatedCycleLength) + 1;
            String phase = resolvePhase(normalizedDay, periodLength, estimatedCycleLength);

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

        List<CycleRecordInsightResponse.PhaseSymptomImpact> phaseImpacts = List.of("Kinh nguyệt", "Nang trứng", "Rụng trứng", "Hoàng thể")
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
            record.setPeriodLength(CycleRecordStatus.ONGOING.equals(record.getStatus()) ? 1 : DEFAULT_PERIOD_LENGTH);
        }
        if (CycleRecordStatus.ONGOING.equals(record.getStatus())) {
            record.setEndDate(null);
            if (record.getLastBleedingDate() == null) {
                record.setLastBleedingDate(record.getStartDate());
            }
            record.setPeriodLength(daysInclusive(record.getStartDate(), record.getLastBleedingDate()));
        } else if (record.getStatus() == null && record.getEndDate() != null) {
            record.setStatus(CycleRecordStatus.COMPLETED);
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

    private void validate(LocalDate startDate, LocalDate endDate, Integer cycleLength, Integer periodLength) {
        if (startDate == null) {
            throw new IllegalArgumentException("Ngày bắt đầu là bắt buộc");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được ở tương lai");
        }
        if (endDate != null && endDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày kết thúc không được ở tương lai");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (!isPlausibleCycleLength(cycleLength) && cycleLength != null) {
            throw new IllegalArgumentException("Độ dài chu kỳ phải từ 10 đến 90 ngày");
        }
        if (!isPlausiblePeriodLength(periodLength) && periodLength != null) {
            throw new IllegalArgumentException("Độ dài kỳ kinh phải từ 1 đến 30 ngày");
        }
    }

    private int roundedAverageOrFallback(List<Integer> values, Integer fallback, int defaultValue) {
        if (!values.isEmpty()) {
            return Math.max(1, (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(defaultValue)));
        }
        return fallback != null ? fallback : defaultValue;
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

    private boolean isPlausiblePeriodLength(Integer value) {
        return value != null && value >= MIN_PERIOD_LENGTH && value <= MAX_PERIOD_LENGTH;
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
}
