package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "couple_place_reports")
@CompoundIndexes({
        @CompoundIndex(name = "couple_place_report_unique_idx", def = "{ 'placeId': 1, 'userId': 1, 'status': 1 }"),
        @CompoundIndex(name = "couple_place_report_status_idx", def = "{ 'status': 1, 'createdAt': -1 }")
})
public class CouplePlaceReport {

    @Id
    @JsonProperty("_id")
    private Long id;

    private Long placeId;
    private String targetType = "PLACE";
    private Long targetId;
    private String userId;
    private String userName;
    private String reason = "";
    private CouplePlaceReportStatus status = CouplePlaceReportStatus.OPEN;

    @CreatedDate
    private Instant createdAt;
}
