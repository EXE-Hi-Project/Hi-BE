package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertAiCostRequest {
    @NotBlank
    private String month;

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;

    @NotNull
    private Double costUsd;

    private String notes = "";
}
