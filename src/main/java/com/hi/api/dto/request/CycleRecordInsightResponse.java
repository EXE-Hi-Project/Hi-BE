package com.hi.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class CycleRecordInsightResponse {
    long cycleCount;
    Double averageCycleLength;
    Double averagePeriodLength;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate lastStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate lastRecordedStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate lastRecordedEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedCurrentCycleStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedPeriodStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedPeriodEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedNextStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedNextEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate estimatedOvulationDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate fertileWindowStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate fertileWindowEndDate;

    Integer currentCycleDay;
    String currentPhase;
    String periodStatus;
    Integer confirmedPeriodDay;
    Integer estimatedCycleDay;
    String estimatedPhase;
    Integer periodDelayDays;
    Integer daysUntilEstimatedPeriod;
    Integer estimatedPeriodDay;
    String fertilityStatus;
    String predictionConfidence;
    boolean hasOutliers;
    List<String> warnings;

    Double symptomImpactScore;
    List<PhaseSymptomImpact> phaseSymptomImpacts;
    List<SymptomImpactItem> topSymptoms;

    @Value
    @Builder
    public static class PhaseSymptomImpact {
        String phase;
        Double impactScore;
        long occurrenceCount;
    }

    @Value
    @Builder
    public static class SymptomImpactItem {
        Long symptomId;
        String symptomName;
        Double impactScore;
        Double averageSeverity;
        long occurrenceCount;
    }
}
