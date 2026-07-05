package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "google_place_cache")
public class GooglePlaceCache {

    @Id
    private String cacheKey;

    private Double lat;
    private Double lng;
    private Integer radius;
    private CouplePlaceCategory category;
    private List<CachedGooglePlace> places = new ArrayList<>();

    @Indexed
    private Instant expiresAt;

    @Data
    @NoArgsConstructor
    public static class CachedGooglePlace {
        private String googlePlaceId;
        private String name;
        private String address;
        private Double lat;
        private Double lng;
        private Double rating;
        private Integer userRatingCount;
        private String googleMapsUri;
        private List<String> types = new ArrayList<>();
    }
}
