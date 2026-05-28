package com.hi.api.dto.request;

import lombok.Data;

@Data
public class UpdateCycleRequest {
    private String startDate;
    private String endDate;
    private Integer cycleLength;
    private Integer periodLength;
    private String notes;
}
