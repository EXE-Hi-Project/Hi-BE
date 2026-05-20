package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCycleRequest {
    @NotBlank(message = "Ngày bắt đầu là bắt buộc")
    private String startDate;
    private String endDate;
    private Integer cycleLength;
    private String notes;
}
