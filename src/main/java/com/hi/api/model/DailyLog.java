package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "daily_logs")
@CompoundIndexes({
        @CompoundIndex(name = "daily_log_user_date_idx", def = "{ 'userId': 1, 'logDate': -1 }", unique = true)
})
public class DailyLog {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private String userId;

    @Indexed
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate logDate;

    private FlowIntensity flowIntensity = FlowIntensity.NONE;

    private Integer moodScore;

    private String notes = "";

    @Transient
    private List<DailyLogSymptom> symptoms = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}