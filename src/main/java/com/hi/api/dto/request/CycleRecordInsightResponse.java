package com.hi.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

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
}