package com.hi.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hi.api.model.CycleRecordStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateCycleRecordRequest {

    @NotNull(message = "Ngày bắt đầu là bắt buộc")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Min(value = 10, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    @Max(value = 90, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    private Integer cycleLength;

    @Min(value = 1, message = "Độ dài kỳ kinh phải từ 1 ngày trở lên")
    private Integer periodLength;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String notes;

    private CycleRecordStatus status;

    private Boolean isIgnored;
}
