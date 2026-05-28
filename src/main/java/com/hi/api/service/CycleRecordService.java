package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.CreateCycleRecordRequest;
import com.hi.api.dto.request.UpdateCycleRecordRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.SymptomSeverity;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CycleRecordService {

    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final SequenceService sequenceService;

    public CycleRecordService(CycleRecordRepository cycleRecordRepository,
                              DailyLogRepository dailyLogRepository,
                              DailyLogSymptomRepository dailyLogSymptomRepository,
                              SymptomDictionaryRepository symptomDictionaryRepository,
                              SequenceService sequenceService) {
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.sequenceService = sequenceService;
    }

    public List<CycleRecord> getCycleRecords(String userId, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return cycleRecordRepository.findByUserIdAndStartDateBetweenOrderByStartDateDesc(userId, from, to);
        }
        if (from != null) {
            return cycleRecordRepository.findByUserIdAndStartDateGreaterThanEqualOrderByStartDateDesc(userId, from);
        }
        if (to != null) {
            return cycleRecordRepository.findByUserIdAndStartDateLessThanEqualOrderByStartDateDesc(userId, to);
        }
        return cycleRecordRepository.findByUserIdOrderByStartDateDesc(userId);
    }

    public CycleRecord createCycleRecord(String userId, CreateCycleRecordRequest req) {
        CycleRecord record = new CycleRecord();
        record.setId(sequenceService.next("cycle_records"));
        record.setUserId(userId);
        apply(record, req.getStartDate(), req.getEndDate(), req.getCycleLength(), req.getPeriodLength(), req.getIsIgnored());
        return cycleRecordRepository.save(record);
    }

    public CycleRecord updateCycleRecord(String userId, Long id, UpdateCycleRecordRequest req) {
        CycleRecord record = cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));
        apply(record, req.getStartDate(), req.getEndDate(), req.getCycleLength(), req.getPeriodLength(), req.getIsIgnored());
        return cycleRecordRepository.save(record);
    }

    public void deleteCycleRecord(String userId, Long id) {
        CycleRecord record = cycleRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));
        cycleRecordRepository.delete(record);
    }

    public CycleRecordInsightResponse getInsights(String userId) {
        List<CycleRecord> records = cycleRecordRepository.findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(userId);
        if (records.isEmpty()) {
            return CycleRecordInsightResponse.builder()
                    .cycleCount(0)
                    .averageCycleLength(null)
                    .averagePeriodLength(null)
                    .symptomImpactScore(0.0)
                    .phaseSymptomImpacts(List.of())
                    .topSymptoms(List.of())
                    .build();
        }

        List<CycleRecord> sorted = records.stream()
                .sorted(Comparator.comparing(CycleRecord::getStartDate))
                .toList();

        Double averageCycleLength = sorted.stream()
                .map(CycleRecord::getCycleLength)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);

        Double averagePeriodLength = sorted.stream()
                .map(CycleRecord::getPeriodLength)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.NaN);

        CycleRecord latest = sorted.get(sorted.size() - 1);
        LocalDate lastStartDate = latest.getStartDate();

        LocalDate predictedNextStartDate = null;
        LocalDate predictedNextEndDate = null;

        if (averageCycleLength != null && !averageCycleLength.isNaN() && lastStartDate != null) {
            predictedNextStartDate = lastStartDate.plusDays(Math.round(averageCycleLength));
            if (averagePeriodLength != null && !averagePeriodLength.isNaN()) {
                predictedNextEndDate = predictedNextStartDate.plusDays(Math.max(1L, Math.round(averagePeriodLength)) - 1);
            }
        }

        SymptomAnalytics analytics = analyzeSymptoms(userId, sorted,
                averageCycleLength != null && !averageCycleLength.isNaN() ? averageCycleLength : null,
                averagePeriodLength != null && !averagePeriodLength.isNaN() ? averagePeriodLength : null);

        return CycleRecordInsightResponse.builder()
                .cycleCount(sorted.size())
                .averageCycleLength(averageCycleLength != null && !averageCycleLength.isNaN() ? round2(averageCycleLength) : null)
                .averagePeriodLength(averagePeriodLength != null && !averagePeriodLength.isNaN() ? round2(averagePeriodLength) : null)
                .lastStartDate(lastStartDate)
                .predictedNextStartDate(predictedNextStartDate)
                .predictedNextEndDate(predictedNextEndDate)
                .symptomImpactScore(analytics.overallImpactScore)
                .phaseSymptomImpacts(analytics.phaseImpacts)
                .topSymptoms(analytics.topSymptoms)
                .build();
    }

    private SymptomAnalytics analyzeSymptoms(String userId,
                                             List<CycleRecord> sortedRecords,
                                             Double averageCycleLength,
                                             Double averagePeriodLength) {
        LocalDate from = sortedRecords.get(Math.max(0, sortedRecords.size() - 6)).getStartDate();
        LocalDate to = LocalDate.now();
        List<DailyLog> logs = dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, from, to);

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
            if (anchor == null || anchor.getStartDate() == null) {
                continue;
            }

            int periodLength = anchor.getPeriodLength() != null
                    ? anchor.getPeriodLength()
                    : (averagePeriodLength != null ? Math.max(1, (int) Math.round(averagePeriodLength)) : 5);

            long dayLong = ChronoUnit.DAYS.between(anchor.getStartDate(), log.getLogDate()) + 1;
            int day = (int) Math.max(1, dayLong);
            String phase = resolvePhase(day, periodLength);

            double flowScore = flowWeight(log.getFlowIntensity());
            double moodScore = moodWeight(log.getMoodScore());
            double logBaseScore = flowScore + moodScore;

            Aggregate phaseAggregate = phaseAgg.computeIfAbsent(phase, ignored -> new Aggregate());
            phaseAggregate.total += logBaseScore;
            phaseAggregate.occurrences += 1;
            totalScore += logBaseScore;

            List<DailyLogSymptom> relations = symptomByLogId.getOrDefault(log.getId(), List.of());
            for (DailyLogSymptom relation : relations) {
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

        List<CycleRecordInsightResponse.PhaseSymptomImpact> phaseImpacts = List.of("Kinh nguyet", "Nang trung", "Rung trung", "Hoang the")
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
                    double avgSeverity = agg.occurrences == 0 ? 0.0 : round2(agg.severityTotal / agg.occurrences);
                    return CycleRecordInsightResponse.SymptomImpactItem.builder()
                            .symptomId(entry.getKey())
                            .symptomName(dictionary != null ? dictionary.getName() : "Trieu chung #" + entry.getKey())
                            .impactScore(impact)
                            .averageSeverity(avgSeverity)
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
            if (record.getStartDate() == null) {
                continue;
            }
            if (!record.getStartDate().isAfter(logDate)) {
                anchor = record;
            } else {
                break;
            }
        }
        return anchor;
    }

    private String resolvePhase(int cycleDay, int periodLength) {
        if (cycleDay <= periodLength) {
            return "Kinh nguyet";
        }
        if (cycleDay <= 12) {
            return "Nang trung";
        }
        if (cycleDay <= 16) {
            return "Rung trung";
        }
        return "Hoang the";
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

    private void apply(CycleRecord record, LocalDate startDate, LocalDate endDate, Integer cycleLength, Integer periodLength, Boolean isIgnored) {
        if (startDate != null) {
            record.setStartDate(startDate);
        }
        if (endDate != null) {
            record.setEndDate(endDate);
            if (record.getStartDate() != null && periodLength == null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(record.getStartDate(), endDate) + 1;
                record.setPeriodLength((int) days);
            }
        }
        if (cycleLength != null) {
            record.setCycleLength(cycleLength);
        }
        if (periodLength != null) {
            record.setPeriodLength(periodLength);
        }
        if (isIgnored != null) {
            record.setIsIgnored(isIgnored);
        }
        if (record.getIsIgnored() == null) {
            record.setIsIgnored(false);
        }
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
}