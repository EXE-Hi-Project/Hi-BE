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
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "cycle_records")
@CompoundIndexes({
        @CompoundIndex(name = "cycle_record_user_start_idx", def = "{ 'userId': 1, 'startDate': 1 }", unique = true)
})
public class CycleRecord {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private String userId;

    @Indexed
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Indexed
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Integer cycleLength;

    private Integer periodLength;

    @Indexed
    private Boolean isIgnored = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
