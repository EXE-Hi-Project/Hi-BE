package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "daily_questions")
@CompoundIndex(name = "daily_question_order_idx", def = "{ 'active': 1, 'displayOrder': 1 }", unique = true)
public class DailyQuestion {

    @Id
    @JsonProperty("_id")
    private String id;

    private String category;
    private String prompt;
    private Integer displayOrder;
    private Boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
