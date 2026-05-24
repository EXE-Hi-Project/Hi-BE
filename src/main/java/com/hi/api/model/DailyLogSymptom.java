package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "daily_log_symptoms")
@CompoundIndexes({
        @CompoundIndex(name = "daily_log_symptom_unique_idx", def = "{ 'dailyLogId': 1, 'symptomId': 1 }", unique = true),
        @CompoundIndex(name = "daily_log_symptom_symptom_idx", def = "{ 'symptomId': 1 }")
})
public class DailyLogSymptom {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private Long dailyLogId;

    @Indexed
    private Long symptomId;

    private SymptomSeverity severity = SymptomSeverity.MILD;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}