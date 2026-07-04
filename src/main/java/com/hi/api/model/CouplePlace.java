package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "couple_places")
@CompoundIndexes({
        @CompoundIndex(name = "couple_place_status_category_idx", def = "{ 'status': 1, 'category': 1 }"),
        @CompoundIndex(name = "couple_place_google_idx", def = "{ 'googlePlaceId': 1 }", sparse = true),
        @CompoundIndex(name = "couple_place_visibility_pair_idx", def = "{ 'visibility': 1, 'pairKey': 1 }")
})
public class CouplePlace {

    @Id
    @JsonProperty("_id")
    private Long id;

    private String name;
    private String description = "";

    @Indexed
    private CouplePlaceCategory category = CouplePlaceCategory.OTHER;

    private Location location = new Location();
    private CouplePlaceSource source = CouplePlaceSource.USER;
    private CouplePlaceVisibility visibility = CouplePlaceVisibility.PUBLIC;
    @JsonIgnore
    private String pairKey;
    @JsonIgnore
    private List<String> privateMemberIds = new ArrayList<>();
    private String googlePlaceId;
    private Double googleRating;
    private Integer googleUserRatingCount;
    private String googleMapsUri;
    private Double userRatingAvg = 0.0;
    private Integer reviewCount = 0;
    private Integer likeCount = 0;
    private Integer dislikeCount = 0;
    private Integer saveCount = 0;
    private Integer reportCount = 0;

    @Indexed
    private CouplePlaceStatus status = CouplePlaceStatus.PUBLISHED;

    private String createdBy;
    private String createdByName;
    private List<String> tags = new ArrayList<>();
    private String coverPhotoUrl = "";

    @Transient
    private Double distanceMeters;

    @Transient
    private Boolean likedByMe = false;

    @Transient
    private Boolean dislikedByMe = false;

    @Transient
    private Boolean savedByMe = false;

    @Transient
    private Boolean ownedByMe = false;

    @Transient
    private List<CouplePlacePhoto> photos = new ArrayList<>();

    @Transient
    private List<CouplePlaceReview> recentReviews = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    public static class Location {
        private Double lat;
        private Double lng;
        private String address = "";
        private String city = "";
        private String district = "";
    }
}
