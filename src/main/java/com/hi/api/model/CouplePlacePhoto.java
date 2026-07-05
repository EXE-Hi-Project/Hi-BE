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
@Document(collection = "couple_place_photos")
@CompoundIndexes({
        @CompoundIndex(name = "couple_place_photo_place_status_idx", def = "{ 'placeId': 1, 'status': 1, 'createdAt': -1 }")
})
public class CouplePlacePhoto {

    @Id
    @JsonProperty("_id")
    private Long id;

    private Long placeId;
    private String userId;
    private String userName;
    private String objectKey;
    private String url;
    private String contentType;
    private CouplePlaceStatus status = CouplePlaceStatus.PUBLISHED;

    @CreatedDate
    private Instant createdAt;
}
