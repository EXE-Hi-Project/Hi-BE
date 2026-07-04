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
@Document(collection = "couple_place_reviews")
@CompoundIndexes({
        @CompoundIndex(name = "couple_place_review_place_status_idx", def = "{ 'placeId': 1, 'status': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "couple_place_review_user_place_idx", def = "{ 'userId': 1, 'placeId': 1 }")
})
public class CouplePlaceReview {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private Long placeId;

    private String userId;
    private String userName;
    private Integer rating;
    private String content = "";
    private CouplePlaceStatus status = CouplePlaceStatus.PUBLISHED;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
