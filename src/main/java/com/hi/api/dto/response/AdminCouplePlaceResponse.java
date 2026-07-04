package com.hi.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlacePhoto;
import com.hi.api.model.CouplePlaceSource;
import com.hi.api.model.CouplePlaceStatus;
import com.hi.api.model.CouplePlaceVisibility;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminCouplePlaceResponse {

    @JsonProperty("_id")
    private Long id;
    private String name;
    private String description;
    private CouplePlaceCategory category;
    private CouplePlace.Location location;
    private CouplePlaceSource source;
    private CouplePlaceVisibility visibility;
    private Double googleRating;
    private Integer googleUserRatingCount;
    private Double userRatingAvg;
    private Integer reviewCount;
    private Integer likeCount;
    private Integer dislikeCount;
    private Integer saveCount;
    private Integer reportCount;
    private CouplePlaceStatus status;
    private String createdBy;
    private String createdByName;
    private List<String> tags = new ArrayList<>();
    private String coverPhotoUrl;
    private List<CouplePlacePhoto> photos = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private boolean metadataOnly;

    public static AdminCouplePlaceResponse from(CouplePlace place, boolean metadataOnly) {
        AdminCouplePlaceResponse response = new AdminCouplePlaceResponse();
        response.setId(place.getId());
        response.setName(place.getName());
        response.setCategory(place.getCategory());
        response.setVisibility(place.getVisibility() == null ? CouplePlaceVisibility.PUBLIC : place.getVisibility());
        response.setUserRatingAvg(place.getUserRatingAvg());
        response.setReviewCount(place.getReviewCount());
        response.setLikeCount(place.getLikeCount());
        response.setDislikeCount(place.getDislikeCount());
        response.setSaveCount(place.getSaveCount());
        response.setReportCount(place.getReportCount());
        response.setStatus(place.getStatus());
        response.setCreatedBy(place.getCreatedBy());
        response.setCreatedByName(place.getCreatedByName());
        response.setCreatedAt(place.getCreatedAt());
        response.setUpdatedAt(place.getUpdatedAt());
        response.setMetadataOnly(metadataOnly);
        if (!metadataOnly) {
            response.setDescription(place.getDescription());
            response.setLocation(place.getLocation());
            response.setSource(place.getSource());
            response.setGoogleRating(place.getGoogleRating());
            response.setGoogleUserRatingCount(place.getGoogleUserRatingCount());
            response.setTags(place.getTags());
            response.setCoverPhotoUrl(place.getCoverPhotoUrl());
            response.setPhotos(place.getPhotos());
        }
        return response;
    }
}
