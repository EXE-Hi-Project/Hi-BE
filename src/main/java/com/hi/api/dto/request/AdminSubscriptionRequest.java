package com.hi.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AdminSubscriptionRequest {

    @NotBlank
    private String plan;

    @NotNull
    @Min(1)
    @Max(366)
    private Integer durationDays;

    @Size(max = 300)
    private String reason;
}