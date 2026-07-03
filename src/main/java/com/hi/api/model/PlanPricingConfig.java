package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "plan_pricing")
public class PlanPricingConfig {
    public static final String DEFAULT_ID = "default";

    @Id
    private String id = DEFAULT_ID;
    private Long hiProBasePrice = 49_000L;
    private Long hiMaxBasePrice = 399_000L;
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;
}
