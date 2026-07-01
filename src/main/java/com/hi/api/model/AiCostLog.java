package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "ai_cost_logs")
public class AiCostLog {

    @Id
    private String id;

    @Indexed(unique = true)
    private String month; // format: "yyyy-MM" (e.g. "2026-06")

    private Long inputTokens = 0L;

    private Long outputTokens = 0L;

    private Long totalTokens = 0L;

    private Double costUsd = 0.0;

    private String notes = "";

    @CreatedDate
    private Instant createdAt;
}
