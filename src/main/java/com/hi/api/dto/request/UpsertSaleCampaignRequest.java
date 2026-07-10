package com.hi.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpsertSaleCampaignRequest {
    @NotBlank
    @Size(max = 80)
    private String name;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 180)
    private String subtitle;

    @NotNull
    @Min(0)
    private Long hiProSalePrice;

    @NotNull
    @Min(0)
    private Long hiMaxSalePrice;

    @NotNull
    private Instant startsAt;

    @NotNull
    private Instant endsAt;
}
