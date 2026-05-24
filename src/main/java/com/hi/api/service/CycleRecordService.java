package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.CreateCycleRecordRequest;
import com.hi.api.dto.request.UpdateCycleRecordRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.repository.CycleRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CycleRecordService {

    private final CycleRecordRepository cycleRecordRepository;
    private final SequenceService sequenceService;

    public CycleRecordService(CycleRecordRepository cycleRecordRepository, SequenceService sequenceService) {
        this.cycleRecordRepository = cycleRecordRepository;
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

        return CycleRecordInsightResponse.builder()
                .cycleCount(sorted.size())
                .averageCycleLength(averageCycleLength != null && !averageCycleLength.isNaN() ? round2(averageCycleLength) : null)
                .averagePeriodLength(averagePeriodLength != null && !averagePeriodLength.isNaN() ? round2(averagePeriodLength) : null)
                .lastStartDate(lastStartDate)
                .predictedNextStartDate(predictedNextStartDate)
                .predictedNextEndDate(predictedNextEndDate)
                .build();
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
}