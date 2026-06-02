package com.hi.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

@Data
public class UpdateCycleRecordRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Min(value = 10, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    @Max(value = 90, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    private Integer cycleLength;

    @Min(value = 1, message = "Độ dài kỳ kinh phải từ 1 đến 30 ngày")
    @Max(value = 30, message = "Độ dài kỳ kinh phải từ 1 đến 30 ngày")
    private Integer periodLength;

    private Boolean isIgnored;
}
