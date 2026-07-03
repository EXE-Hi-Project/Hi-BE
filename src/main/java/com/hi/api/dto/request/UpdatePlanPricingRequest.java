package com.hi.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePlanPricingRequest {
    @NotNull
    @Min(1_000)
    private Long hiProBasePrice;

    @NotNull
    @Min(1_000)
    private Long hiMaxBasePrice;
}
