package com.hi.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateCycleRecordRequest {

    @NotNull(message = "Ngày bắt đầu là bắt buộc")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Integer cycleLength;

    private Integer periodLength;

    private Boolean isIgnored;
}