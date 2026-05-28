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
    LocalDate predictedNextStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate predictedNextEndDate;

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