package com.hi.api.service;

import com.hi.api.dto.request.ConfirmCouplePlacePhotoRequest;
import com.hi.api.dto.request.CreateCouplePlaceRequest;
import com.hi.api.dto.request.CreateCouplePlaceReviewRequest;
import com.hi.api.dto.request.PresignCouplePlacePhotoRequest;
import com.hi.api.dto.request.ReportCouplePlaceRequest;
import com.hi.api.dto.response.AdminCouplePlaceResponse;
import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlacePhoto;
import com.hi.api.model.CouplePlaceReaction;
import com.hi.api.model.CouplePlaceReactionType;
import com.hi.api.model.CouplePlaceReport;
import com.hi.api.model.CouplePlaceReportStatus;
import com.hi.api.model.CouplePlaceReview;
import com.hi.api.model.CouplePlaceSource;
import com.hi.api.model.CouplePlaceStatus;
import com.hi.api.model.CouplePlaceVisibility;
import com.hi.api.model.GooglePlaceCache;
import com.hi.api.model.User;
import com.hi.api.repository.CouplePlacePhotoRepository;
import com.hi.api.repository.CouplePlaceReactionRepository;
import com.hi.api.repository.CouplePlaceReportRepository;
import com.hi.api.repository.CouplePlaceRepository;
import com.hi.api.repository.CouplePlaceReviewRepository;
import com.hi.api.repository.GooglePlaceCacheRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CouplePlaceService {

    private static final int DEFAULT_RADIUS_METERS = 10000;
    private static final int MAX_RADIUS_METERS = 20000;
    private static final int MAX_OSM_RESULTS = 25;
    private static final int MAX_ADDRESS_SUGGESTIONS = 8;
    private static final int MAX_ADDRESS_CANDIDATES = 16;
    private static final int MAX_PHOTOS_PER_PLACE = 5;
    private static final long MAX_PHOTO_BYTES = 5L * 1024L * 1024L;
    private static final Duration PHOTO_PRESIGN_TTL = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CouplePlaceRepository placeRepository;
    private final CouplePlaceReviewRepository reviewRepository;
    private final CouplePlaceReactionRepository reactionRepository;
    private final CouplePlaceReportRepository reportRepository;
    private final CouplePlacePhotoRepository photoRepository;
    private final GooglePlaceCacheRepository googleCacheRepository;
    private final SequenceService sequenceService;
    private final RestTemplate restTemplate;
    private final PartnerAccessService partnerAccessService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.osm.overpass-url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    @Value("${app.osm.photon-url:https://photon.komoot.io/api}")
    private String photonUrl;

    @Value("${app.tomtom.search-api-key:}")
    private String tomTomSearchApiKey;

    @Value("${app.tomtom.search-url:https://api.tomtom.com/search/2/search}")
    private String tomTomSearchUrl;

    @Value("${app.vietmap.api-key:}")
    private String vietMapApiKey;

    @Value("${app.vietmap.autocomplete-url:https://maps.vietmap.vn/api/autocomplete/v4}")
    private String vietMapAutocompleteUrl;

    @Value("${app.vietmap.place-url:https://maps.vietmap.vn/api/place/v4}")
    private String vietMapPlaceUrl;

    @Value("${app.osm.cache-ttl-hours:24}")
    private int osmCacheTtlHours;

    @Value("${app.couple-places.report-hide-threshold:3}")
    private int reportHideThreshold;

    @Value("${app.couple-places.photo-upload-enabled:false}")
    private boolean photoUploadEnabled;

    @Value("${app.s3.user-media-bucket:}")
    private String mediaBucket;

    @Value("${app.s3.public-base-url:}")
    private String mediaPublicBaseUrl;

    @Value("${app.s3.region:${aws.region:us-east-1}}")
    private String mediaRegion;

    public CouplePlaceService(CouplePlaceRepository placeRepository,
                              CouplePlaceReviewRepository reviewRepository,
                              CouplePlaceReactionRepository reactionRepository,
                              CouplePlaceReportRepository reportRepository,
                              CouplePlacePhotoRepository photoRepository,
                              GooglePlaceCacheRepository googleCacheRepository,
                              SequenceService sequenceService,
                              RestTemplate restTemplate,
                              PartnerAccessService partnerAccessService,
                              S3Client s3Client,
                              S3Presigner s3Presigner) {
        this.placeRepository = placeRepository;
        this.reviewRepository = reviewRepository;
        this.reactionRepository = reactionRepository;
        this.reportRepository = reportRepository;
        this.photoRepository = photoRepository;
        this.googleCacheRepository = googleCacheRepository;
        this.sequenceService = sequenceService;
        this.restTemplate = restTemplate;
        this.partnerAccessService = partnerAccessService;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public List<CouplePlace> nearby(User user, Double lat, Double lng, Integer radius, CouplePlaceCategory category, String sort) {
        validateCoordinates(lat, lng);
        int safeRadius = safeRadius(radius);
        Bounds bounds = boundsAround(lat, lng, safeRadius);
        String userId = user != null && user.getId() != null ? user.getId() : "__anonymous__";
        List<CouplePlace> candidates = category == null
                ? placeRepository.findVisibleWithinBounds(CouplePlaceStatus.PUBLISHED, userId, bounds.south(), bounds.north(), bounds.west(), bounds.east())
                : placeRepository.findVisibleWithinBoundsAndCategory(CouplePlaceStatus.PUBLISHED, category, userId, bounds.south(), bounds.north(), bounds.west(), bounds.east());
        List<CouplePlace> userPlaces = candidates.stream()
                .filter(place -> canView(user, place))
                .filter(place -> place.getLocation() != null && place.getLocation().getLat() != null && place.getLocation().getLng() != null)
                .peek(place -> place.setDistanceMeters(distanceMeters(lat, lng, place.getLocation().getLat(), place.getLocation().getLng())))
                .filter(place -> place.getDistanceMeters() <= safeRadius)
                .collect(Collectors.toCollection(ArrayList::new));

        List<CouplePlace> osmPlaces = fetchOsmNearby(lat, lng, safeRadius, category);
        Map<String, CouplePlace> internalByGoogleId = userPlaces.stream()
                .filter(place -> place.getGooglePlaceId() != null && !place.getGooglePlaceId().isBlank())
                .collect(Collectors.toMap(CouplePlace::getGooglePlaceId, place -> place, (a, b) -> a));
        for (CouplePlace osmPlace : osmPlaces) {
            if (osmPlace.getGooglePlaceId() == null || !internalByGoogleId.containsKey(osmPlace.getGooglePlaceId())) {
                osmPlace.setDistanceMeters(distanceMeters(lat, lng, osmPlace.getLocation().getLat(), osmPlace.getLocation().getLng()));
                userPlaces.add(osmPlace);
            }
        }

        enrichAll(userPlaces, user, false);
        userPlaces.sort(comparator(sort));
        return userPlaces.stream().limit(60).toList();
    }

    public CouplePlace create(User user, CreateCouplePlaceRequest request) {
        validateCoordinates(request.getLat(), request.getLng());
        CouplePlaceVisibility visibility = request.getVisibility() == null
                ? CouplePlaceVisibility.PUBLIC
                : request.getVisibility();
        User partner = null;
        String pairKey = null;
        if (visibility == CouplePlaceVisibility.COUPLE_PRIVATE) {
            partner = partnerAccessService.requireCurrentPartner(user);
            pairKey = partnerAccessService.pairKey(user.getId(), partner.getId());
        }

        CouplePlace place = findReusablePlace(request.getGooglePlaceId(), visibility, pairKey)
                .orElseGet(CouplePlace::new);

        if (place.getId() == null) {
            place.setId(sequenceService.next("couple_places"));
            place.setCreatedBy(user.getId());
            place.setCreatedByName(displayContributorName(user, request.getAnonymous(), request.getNickname()));
        }

        place.setVisibility(visibility);
        if (visibility == CouplePlaceVisibility.COUPLE_PRIVATE) {
            place.setPairKey(pairKey);
            place.setPrivateMemberIds(List.of(user.getId(), partner.getId()).stream().sorted().toList());
        } else {
            place.setPairKey(null);
            place.setPrivateMemberIds(new ArrayList<>());
        }

        place.setName(cleanRequired(request.getName(), "Ten dia diem la bat buoc"));
        place.setDescription(clean(request.getDescription()));
        place.setCategory(request.getCategory() != null ? request.getCategory() : CouplePlaceCategory.OTHER);
        CouplePlace.Location location = new CouplePlace.Location();
        location.setLat(request.getLat());
        location.setLng(request.getLng());
        location.setAddress(clean(request.getAddress()));
        location.setCity(clean(request.getCity()));
        location.setDistrict(clean(request.getDistrict()));
        place.setLocation(location);
        place.setSource(request.getGooglePlaceId() == null || request.getGooglePlaceId().isBlank()
                ? CouplePlaceSource.USER
                : CouplePlaceSource.HYBRID);
        place.setGooglePlaceId(clean(request.getGooglePlaceId()));
        place.setGoogleRating(request.getGoogleRating());
        place.setGoogleUserRatingCount(request.getGoogleUserRatingCount());
        place.setGoogleMapsUri(clean(request.getGoogleMapsUri()));
        place.setTags(cleanTags(request.getTags()));
        place.setStatus(CouplePlaceStatus.PUBLISHED);
        recalculateStats(place);
        return enrich(placeRepository.save(place), user, true);
    }

    public List<Map<String, Object>> searchAddress(String query, Double lat, Double lng) {
        return searchAddress(null, query, lat, lng);
    }

    public List<Map<String, Object>> searchAddress(User user, String query, Double lat, Double lng) {
        String cleaned = clean(query);
        if (cleaned.length() < 2) {
            return List.of();
        }
        Map<String, Map<String, Object>> suggestions = new LinkedHashMap<>();
        List<CouplePlace> internalCandidates;
        if (isValidCoordinate(lat, lng)) {
            Bounds bounds = boundsAround(lat, lng, MAX_RADIUS_METERS);
            String userId = user != null && user.getId() != null ? user.getId() : "__anonymous__";
            internalCandidates = placeRepository.findVisibleWithinBounds(
                    CouplePlaceStatus.PUBLISHED,
                    userId,
                    bounds.south(),
                    bounds.north(),
                    bounds.west(),
                    bounds.east()
            );
        } else {
            internalCandidates = placeRepository.findByStatus(CouplePlaceStatus.PUBLISHED);
        }
        internalCandidates.stream()
                .filter(place -> canView(user, place))
                .map(place -> toInternalSearchSuggestion(place, lat, lng, cleaned))
                .filter(Objects::nonNull)
                .forEach(suggestion -> addSearchSuggestion(suggestions, suggestion));

        List<Map<String, Object>> vietMapResults = requestVietMapSearch(cleaned, lat, lng);
        int externalResultCount = 0;
        for (int index = 0; index < vietMapResults.size(); index++) {
            if (addSearchSuggestion(suggestions, toVietMapSuggestion(vietMapResults.get(index), cleaned, index))) {
                externalResultCount++;
            }
        }
        if (externalResultCount < 5) {
            List<Map<String, Object>> tomTomResults = requestTomTomSearch(cleaned, lat, lng);
            for (int index = 0; index < tomTomResults.size(); index++) {
                if (addSearchSuggestion(
                        suggestions,
                        toTomTomSuggestion(tomTomResults.get(index), lat, lng, cleaned, 100 + index)
                )) {
                    externalResultCount++;
                }
            }
        }
        if (externalResultCount < MAX_ADDRESS_SUGGESTIONS) {
            List<Map<String, Object>> features = requestPhotonSearch(cleaned, lat, lng);
            for (int index = 0; index < features.size(); index++) {
                addSearchSuggestion(
                        suggestions,
                        toPhotonSuggestion(features.get(index), lat, lng, cleaned, 200 + index)
                );
            }
        }
        return suggestions.values().stream()
                .sorted(searchSuggestionComparator())
                .limit(MAX_ADDRESS_SUGGESTIONS)
                .map(this::publicSearchSuggestion)
                .toList();
    }

    public Map<String, Object> resolveVietMapSuggestion(String refId) {
        String cleanedRefId = clean(refId);
        if (cleanedRefId.isBlank() || cleanedRefId.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ma dia diem VietMap khong hop le");
        }
        if (clean(vietMapApiKey).isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chua cau hinh VietMap");
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(vietMapPlaceUrl)
                    .queryParam("apikey", vietMapApiKey)
                    .queryParam("refid", cleanedRefId)
                    .build()
                    .encode()
                    .toUriString();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = response.getBody();
            Map<String, Object> place = nestedMap(body, "data");
            if (place.isEmpty() && body != null) {
                place = body;
            }
            Double resultLat = firstDouble(place, "lat", "latitude");
            Double resultLng = firstDouble(place, "lng", "lon", "longitude");
            if (!validSearchFocus(resultLat, resultLng)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietMap khong tra ve toa do hop le");
            }
            String name = firstNonBlank(asString(place.get("name")), asString(place.get("display")), "Dia diem");
            String address = firstNonBlank(asString(place.get("address")), asString(place.get("display")));
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("id", "vietmap:" + cleanedRefId);
            suggestion.put("name", name);
            suggestion.put("address", address);
            suggestion.put("displayName", joinNameAndAddress(name, address));
            suggestion.put("lat", resultLat);
            suggestion.put("lng", resultLng);
            suggestion.put("type", firstNonBlank(asString(place.get("type")), asString(place.get("display_type"))));
            suggestion.put("source", "VIETMAP");
            suggestion.put("requiresResolve", false);
            return publicSearchSuggestion(suggestion);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Khong the lay toa do tu VietMap", ex);
        }
    }

    private Map<String, Object> toInternalSearchSuggestion(CouplePlace place,
                                                            Double lat,
                                                            Double lng,
                                                            String originalQuery) {
        if (place.getLocation() == null || place.getLocation().getLat() == null || place.getLocation().getLng() == null) {
            return null;
        }
        String name = clean(place.getName());
        String address = clean(place.getLocation().getAddress());
        String displayName = joinNameAndAddress(name, address);
        int queryScore = matchScore(originalQuery, displayName);
        if (queryScore <= 0) {
            return null;
        }
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", "hi:" + place.getId());
        suggestion.put("name", name);
        suggestion.put("address", address);
        suggestion.put("displayName", displayName);
        suggestion.put("lat", place.getLocation().getLat());
        suggestion.put("lng", place.getLocation().getLng());
        suggestion.put("type", place.getCategory() == null ? CouplePlaceCategory.OTHER.name() : place.getCategory().name());
        suggestion.put("source", "HI");
        suggestion.put("visibility", effectiveVisibility(place).name());
        suggestion.put("matchScore", queryScore + 15);
        suggestion.put("importance", popularScore(place));
        suggestion.put("providerRank", -100);
        if (validSearchFocus(lat, lng)) {
            suggestion.put("distanceMeters", distanceMeters(lat, lng, place.getLocation().getLat(), place.getLocation().getLng()));
        }
        return suggestion;
    }

    private boolean addSearchSuggestion(Map<String, Map<String, Object>> suggestions, Map<String, Object> suggestion) {
        boolean requiresResolve = Boolean.TRUE.equals(suggestion.get("requiresResolve"));
        if ((!requiresResolve && (suggestion.get("lat") == null || suggestion.get("lng") == null))
                || (requiresResolve && asString(suggestion.get("refId")).isBlank())
                || asString(suggestion.get("displayName")).isBlank()) {
            return false;
        }
        if (isDuplicateSearchSuggestion(suggestions.values(), suggestion)) {
            return false;
        }
        return suggestions.putIfAbsent(suggestionKey(suggestion), suggestion) == null;
    }

    private boolean isDuplicateSearchSuggestion(Iterable<Map<String, Object>> existingSuggestions,
                                                Map<String, Object> candidate) {
        String candidateName = searchSuggestionName(candidate);
        Double candidateLat = asDouble(candidate.get("lat"));
        Double candidateLng = asDouble(candidate.get("lng"));
        for (Map<String, Object> existing : existingSuggestions) {
            if (!candidateName.equals(searchSuggestionName(existing))) {
                continue;
            }
            Double existingLat = asDouble(existing.get("lat"));
            Double existingLng = asDouble(existing.get("lng"));
            if (candidateLat != null && candidateLng != null && existingLat != null && existingLng != null
                    && distanceMeters(candidateLat, candidateLng, existingLat, existingLng) <= 100) {
                return true;
            }
        }
        return false;
    }

    private String searchSuggestionName(Map<String, Object> suggestion) {
        String name = firstNonBlank(asString(suggestion.get("name")), asString(suggestion.get("displayName")));
        return removeDiacritics(name).toLowerCase(Locale.ROOT).trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestVietMapSearch(String query, Double lat, Double lng) {
        if (clean(vietMapApiKey).isBlank()) {
            return List.of();
        }
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(vietMapAutocompleteUrl)
                    .queryParam("apikey", vietMapApiKey)
                    .queryParam("text", query)
                    .queryParam("display_type", 5);
            if (validSearchFocus(lat, lng)) {
                urlBuilder.queryParam("focus", lat + "," + lng);
            }
            ResponseEntity<Object> response = restTemplate.exchange(
                    urlBuilder.build().encode().toUriString(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Object.class
            );
            Object body = response.getBody();
            Object rawItems = body;
            if (body instanceof Map<?, ?> bodyMap) {
                rawItems = bodyMap.get("data") != null ? bodyMap.get("data") : bodyMap.get("results");
            }
            if (!(rawItems instanceof List<?> items)) {
                return List.of();
            }
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .limit(MAX_ADDRESS_CANDIDATES)
                    .toList();
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private Map<String, Object> toVietMapSuggestion(Map<String, Object> result,
                                                     String originalQuery,
                                                     int providerRank) {
        String refId = firstNonBlank(asString(result.get("ref_id")), asString(result.get("refId")));
        String name = firstNonBlank(asString(result.get("name")), asString(result.get("display")), asString(result.get("address")), "Dia diem");
        String address = firstNonBlank(asString(result.get("address")), asString(result.get("display")));
        String displayName = joinNameAndAddress(name, address);
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", "vietmap:" + refId);
        suggestion.put("name", name);
        suggestion.put("address", address);
        suggestion.put("displayName", displayName);
        suggestion.put("type", firstNonBlank(asString(result.get("type")), asString(result.get("display_type"))));
        suggestion.put("source", "VIETMAP");
        suggestion.put("refId", refId);
        suggestion.put("requiresResolve", true);
        suggestion.put("matchScore", matchScore(originalQuery, displayName));
        suggestion.put("providerRank", providerRank);
        Double distanceKm = asDouble(result.get("distance"));
        if (distanceKm != null) {
            suggestion.put("distanceMeters", distanceKm * 1000);
        }
        return suggestion;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestTomTomSearch(String query, Double lat, Double lng) {
        if (clean(tomTomSearchApiKey).isBlank()) {
            return List.of();
        }
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(tomTomSearchUrl)
                    .pathSegment(query + ".json")
                    .queryParam("key", tomTomSearchApiKey)
                    .queryParam("typeahead", true)
                    .queryParam("limit", MAX_ADDRESS_CANDIDATES)
                    .queryParam("countrySet", "VN")
                    .queryParam("language", "vi-VN")
                    .queryParam("idxSet", "POI,PAD,Addr,Str,XStr,Geo");
            if (validSearchFocus(lat, lng)) {
                urlBuilder.queryParam("lat", lat).queryParam("lon", lng);
            }
            String url = urlBuilder.build().encode().toUriString();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !(body.get("results") instanceof List<?> results)) {
                return List.of();
            }
            return (List<Map<String, Object>>) results.stream()
                    .filter(Map.class::isInstance)
                    .map(result -> (Map<String, Object>) result)
                    .toList();
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toTomTomSuggestion(Map<String, Object> result,
                                                   Double lat,
                                                   Double lng,
                                                   String originalQuery,
                                                   int providerRank) {
        Map<String, Object> position = result.get("position") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        Map<String, Object> poi = result.get("poi") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        Map<String, Object> address = result.get("address") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        Double resultLat = asDouble(position.get("lat"));
        Double resultLng = asDouble(position.get("lon"));

        String name = firstNonBlank(asString(poi.get("name")), asString(address.get("streetName")));
        String freeformAddress = asString(address.get("freeformAddress"));
        if (name.isBlank()) {
            name = firstNonBlank(freeformAddress, asString(address.get("municipality")), "Địa điểm");
        }
        String addressText = freeformAddress;
        if (addressText.isBlank()) {
            addressText = collapseSpaces(firstNonBlank(asString(address.get("municipalitySubdivision")), asString(address.get("municipality"))));
        }
        String displayName = joinNameAndAddress(name, addressText);

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", "tomtom:" + asString(result.get("id")));
        suggestion.put("name", name);
        suggestion.put("address", addressText);
        suggestion.put("displayName", displayName);
        suggestion.put("lat", resultLat);
        suggestion.put("lng", resultLng);
        suggestion.put("type", tomTomType(result, poi));
        suggestion.put("source", "TOMTOM");
        suggestion.put("matchScore", matchScore(originalQuery, displayName));
        suggestion.put("importance", asDouble(result.get("score")) == null ? 0.0 : asDouble(result.get("score")));
        suggestion.put("providerRank", providerRank);
        Double providerDistance = asDouble(result.get("dist"));
        if (providerDistance != null) {
            suggestion.put("distanceMeters", providerDistance);
        } else if (validSearchFocus(lat, lng) && resultLat != null && resultLng != null) {
            suggestion.put("distanceMeters", distanceMeters(lat, lng, resultLat, resultLng));
        }
        return suggestion;
    }

    private String tomTomType(Map<String, Object> result, Map<String, Object> poi) {
        Object categories = poi.get("categories");
        if (categories instanceof List<?> values && !values.isEmpty()) {
            return asString(values.get(0));
        }
        return asString(result.get("type"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestPhotonSearch(String query, Double lat, Double lng) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Hi-Lover/1.0 contact@hilover.space");
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(photonUrl)
                    .queryParam("q", query)
                    .queryParam("limit", MAX_ADDRESS_CANDIDATES)
                    .queryParam("countrycode", "VN")
                    .queryParam("dedupe", 1);
            if (validSearchFocus(lat, lng)) {
                urlBuilder.queryParam("lat", lat)
                        .queryParam("lon", lng)
                        .queryParam("zoom", 12)
                        .queryParam("location_bias_scale", 0.2);
            }
            String url = urlBuilder.build().toUriString();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !(body.get("features") instanceof List<?> features)) {
                return List.of();
            }
            return (List<Map<String, Object>>) features.stream()
                    .filter(Map.class::isInstance)
                    .map(feature -> (Map<String, Object>) feature)
                    .toList();
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPhotonSuggestion(Map<String, Object> feature,
                                                   Double lat,
                                                   Double lng,
                                                   String originalQuery,
                                                   int providerRank) {
        Map<String, Object> properties = feature.get("properties") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        Map<String, Object> geometry = feature.get("geometry") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        List<?> coordinates = geometry.get("coordinates") instanceof List<?> value ? value : List.of();
        Double resultLng = coordinates.size() > 0 ? asDouble(coordinates.get(0)) : null;
        Double resultLat = coordinates.size() > 1 ? asDouble(coordinates.get(1)) : null;
        String name = photonName(properties);
        String address = photonAddress(properties, name);
        String displayName = joinNameAndAddress(name, address);
        int queryScore = matchScore(originalQuery, displayName);
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", photonId(properties, resultLat, resultLng));
        suggestion.put("name", name);
        suggestion.put("address", address);
        suggestion.put("displayName", displayName);
        suggestion.put("lat", resultLat);
        suggestion.put("lng", resultLng);
        suggestion.put("type", firstNonBlank(asString(properties.get("osm_value")), asString(properties.get("osm_key"))));
        suggestion.put("source", "PHOTON");
        suggestion.put("matchScore", queryScore);
        suggestion.put("importance", 0.0);
        suggestion.put("providerRank", providerRank);
        if (validSearchFocus(lat, lng) && resultLat != null && resultLng != null) {
            suggestion.put("distanceMeters", distanceMeters(lat, lng, resultLat, resultLng));
        }
        return suggestion;
    }

    private String photonName(Map<String, Object> properties) {
        String street = collapseSpaces(asString(properties.get("housenumber")) + " " + asString(properties.get("street")));
        return firstNonBlank(asString(properties.get("name")), street, asString(properties.get("city")), "Địa điểm");
    }

    private String photonAddress(Map<String, Object> properties, String name) {
        List<String> parts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String street = asString(properties.get("street"));
        String houseNumber = asString(properties.get("housenumber"));
        addDisplayPart(parts, seen, collapseSpaces(houseNumber + " " + street));
        addDisplayPart(parts, seen, asString(properties.get("district")));
        addDisplayPart(parts, seen, asString(properties.get("city")));
        addDisplayPart(parts, seen, asString(properties.get("county")));
        addDisplayPart(parts, seen, asString(properties.get("state")));
        addDisplayPart(parts, seen, asString(properties.get("country")));
        parts.removeIf(part -> removeDiacritics(part).equalsIgnoreCase(removeDiacritics(name)));
        return String.join(", ", parts);
    }

    private String joinNameAndAddress(String name, String address) {
        if (name.isBlank()) return address;
        if (address.isBlank() || removeDiacritics(name).equalsIgnoreCase(removeDiacritics(address))) return name;
        return name + ", " + address;
    }

    private void addDisplayPart(List<String> parts, Set<String> seen, String value) {
        String cleaned = collapseSpaces(clean(value));
        if (cleaned.isBlank()) {
            return;
        }
        String key = removeDiacritics(cleaned).toLowerCase(Locale.ROOT);
        if (seen.add(key)) {
            parts.add(cleaned);
        }
    }

    private String photonId(Map<String, Object> properties, Double lat, Double lng) {
        String osmType = asString(properties.get("osm_type"));
        String osmId = asString(properties.get("osm_id"));
        if (!osmId.isBlank()) {
            return "photon:" + osmType + ":" + osmId;
        }
        return "photon:" + roundCoordinate(lat == null ? 0 : lat) + ":" + roundCoordinate(lng == null ? 0 : lng);
    }

    private boolean validSearchFocus(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private String suggestionKey(Map<String, Object> suggestion) {
        String id = asString(suggestion.get("id"));
        if (!id.isBlank()) {
            return id;
        }
        Double lat = asDouble(suggestion.get("lat"));
        Double lng = asDouble(suggestion.get("lng"));
        return roundCoordinate(lat == null ? 0 : lat) + ":" + roundCoordinate(lng == null ? 0 : lng) + ":" + asString(suggestion.get("displayName")).toLowerCase(Locale.ROOT);
    }

    private Comparator<Map<String, Object>> searchSuggestionComparator() {
        return Comparator
                .<Map<String, Object>>comparingInt(item -> asInteger(item.get("matchScore")) == null ? 0 : asInteger(item.get("matchScore"))).reversed()
                .thenComparingInt(item -> asInteger(item.get("providerRank")) == null ? Integer.MAX_VALUE : asInteger(item.get("providerRank")))
                .thenComparing(item -> asDouble(item.get("distanceMeters")), Comparator.nullsLast(Double::compareTo))
                .thenComparing(Comparator.comparingDouble((Map<String, Object> item) -> {
                    Double importance = asDouble(item.get("importance"));
                    return importance == null ? 0.0 : importance;
                }).reversed());
    }

    private Map<String, Object> publicSearchSuggestion(Map<String, Object> suggestion) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of("id", "name", "address", "displayName", "lat", "lng", "type", "source", "visibility", "distanceMeters", "refId", "requiresResolve")) {
            Object value = suggestion.get(field);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                result.put(field, value);
            }
        }
        result.putIfAbsent("requiresResolve", false);
        return result;
    }

    private int matchScore(String query, String displayName) {
        String normalizedQuery = normalizeSearchText(query);
        String normalizedName = normalizeSearchText(displayName);
        int score = 0;
        if (normalizedName.startsWith(normalizedQuery)) {
            score += 40;
        } else if (normalizedName.contains(normalizedQuery)) {
            score += 20;
        }
        String queryHouseNumber = firstAddressNumber(normalizedQuery);
        String resultHouseNumber = firstAddressNumber(normalizedName);
        if (!queryHouseNumber.isBlank()) {
            if (normalizedName.contains(queryHouseNumber)) {
                score += queryHouseNumber.matches(".*[/.-].*") ? 140 : 55;
            } else if (!resultHouseNumber.isBlank()) {
                String queryParent = queryHouseNumber.split("[/.-]")[0];
                if (resultHouseNumber.equals(queryParent)) {
                    score += 10;
                } else {
                    score -= 60;
                }
            }
        }
        for (String token : normalizedQuery.replaceAll("[/.-]", " ").split("\\s+")) {
            if (token.length() >= 2 && normalizedName.contains(token)) {
                score += 8;
            }
        }
        if (normalizedName.contains("ho chi minh") || normalizedName.contains("thu duc")) {
            score += 5;
        }
        return score;
    }

    private String normalizeSearchText(String value) {
        return collapseSpaces(removeDiacritics(clean(value)).toLowerCase(Locale.ROOT)
                .replaceAll("\\s*([/.-])\\s*", "$1"));
    }

    private String firstAddressNumber(String value) {
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d+[a-z]?(?:[/.-]\\d+[a-z]?)*)(?!\\d)").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> value, String key) {
        if (value != null && value.get(key) instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

    private Double firstDouble(Map<String, Object> value, String... keys) {
        for (String key : keys) {
            Double result = asDouble(value.get(key));
            if (result != null) return result;
        }
        return null;
    }

    public CouplePlace get(User user, Long id) {
        CouplePlace place = getAccessiblePlace(user, id);
        if (place.getStatus() == CouplePlaceStatus.ARCHIVED) {
            throw new IllegalArgumentException("Dia diem khong ton tai");
        }
        return enrich(place, user, true);
    }

    public synchronized CouplePlaceReview addReview(User user, Long placeId, CreateCouplePlaceReviewRequest request) {
        CouplePlace place = getAccessiblePlace(user, placeId);
        if (reviewRepository.existsByPlaceIdAndUserId(placeId, user.getId())) {
            throw new IllegalArgumentException("Bạn đã review địa điểm này");
        }
        CouplePlaceReview review = new CouplePlaceReview();
        review.setId(sequenceService.next("couple_place_reviews"));
        review.setPlaceId(placeId);
        review.setUserId(user.getId());
        review.setUserName(displayContributorName(user, request.getAnonymous(), request.getNickname()));
        review.setRating(request.getRating());
        review.setContent(clean(request.getContent()));
        review.setStatus(CouplePlaceStatus.PUBLISHED);
        CouplePlaceReview saved = reviewRepository.save(review);
        recalculateStats(place);
        placeRepository.save(place);
        return saved;
    }

    public CouplePlace setReaction(User user, Long placeId, CouplePlaceReactionType type, boolean active) {
        CouplePlace place = getAccessiblePlace(user, placeId);
        Optional<CouplePlaceReaction> existing = reactionRepository.findByPlaceIdAndUserIdAndType(placeId, user.getId(), type);
        if (active && existing.isEmpty()) {
            if (type == CouplePlaceReactionType.LIKE || type == CouplePlaceReactionType.DISLIKE) {
                CouplePlaceReactionType opposite = type == CouplePlaceReactionType.LIKE
                        ? CouplePlaceReactionType.DISLIKE
                        : CouplePlaceReactionType.LIKE;
                reactionRepository.findByPlaceIdAndUserIdAndType(placeId, user.getId(), opposite)
                        .ifPresent(reactionRepository::delete);
            }
            CouplePlaceReaction reaction = new CouplePlaceReaction();
            reaction.setId(sequenceService.next("couple_place_reactions"));
            reaction.setPlaceId(placeId);
            reaction.setUserId(user.getId());
            reaction.setType(type);
            reactionRepository.save(reaction);
        }
        if (!active) {
            existing.ifPresent(reactionRepository::delete);
        }
        recalculateStats(place);
        return enrich(placeRepository.save(place), user, false);
    }

    public List<CouplePlace> savedPlaces(User user) {
        List<Long> savedPlaceIds = reactionRepository.findByUserIdAndType(user.getId(), CouplePlaceReactionType.SAVE).stream()
                .map(CouplePlaceReaction::getPlaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<CouplePlace> places = placeRepository.findAllById(savedPlaceIds).stream()
                .filter(place -> place.getStatus() == CouplePlaceStatus.PUBLISHED)
                .filter(place -> canView(user, place))
                .collect(Collectors.toCollection(ArrayList::new));
        enrichAll(places, user, false);
        return places.stream()
                .sorted(Comparator.comparing(CouplePlace::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public CouplePlaceReport report(User user, Long placeId, ReportCouplePlaceRequest request) {
        CouplePlace place = getAccessiblePlace(user, placeId);
        CouplePlaceReport report = reportRepository.findByPlaceIdAndUserIdAndStatus(placeId, user.getId(), CouplePlaceReportStatus.OPEN)
                .orElseGet(CouplePlaceReport::new);
        if (report.getId() == null) {
            report.setId(sequenceService.next("couple_place_reports"));
            report.setPlaceId(placeId);
            report.setTargetId(placeId);
            report.setUserId(user.getId());
            report.setUserName(displayName(user));
        }
        report.setReason(cleanRequired(request.getReason(), "Ly do bao cao la bat buoc"));
        CouplePlaceReport saved = reportRepository.save(report);
        long openReports = reportRepository.countByPlaceIdAndStatus(placeId, CouplePlaceReportStatus.OPEN);
        place.setReportCount((int) openReports);
        if (openReports >= Math.max(1, reportHideThreshold)) {
            place.setStatus(CouplePlaceStatus.HIDDEN);
        }
        placeRepository.save(place);
        return saved;
    }

    public Map<String, Object> presignPhoto(User user, Long placeId, PresignCouplePlacePhotoRequest request) {
        getAccessiblePlace(user, placeId);
        ensurePhotoUploadEnabled();
        if (photoRepository.countByPlaceIdAndStatus(placeId, CouplePlaceStatus.PUBLISHED) >= MAX_PHOTOS_PER_PLACE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Địa điểm đã có tối đa 5 ảnh");
        }
        String contentType = normalizePhotoContentType(request.getContentType());
        validatePhotoLength(request.getContentLength());
        String objectKey = "couple-places/%s/%s/%s%s".formatted(
                placeId,
                user.getId(),
                UUID.randomUUID(),
                photoExtension(contentType, request.getFileName())
        );
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(mediaBucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PHOTO_PRESIGN_TTL)
                .putObjectRequest(putObjectRequest)
                .build();
        return Map.of(
                "uploadUrl", s3Presigner.presignPutObject(presignRequest).url().toString(),
                "objectKey", objectKey,
                "publicUrl", photoPublicUrl(objectKey),
                "contentType", contentType,
                "expiresInSeconds", PHOTO_PRESIGN_TTL.toSeconds()
        );
    }

    public CouplePlacePhoto confirmPhoto(User user, Long placeId, ConfirmCouplePlacePhotoRequest request) {
        getAccessiblePlace(user, placeId);
        ensurePhotoUploadEnabled();
        String objectKey = request.getObjectKey() == null ? "" : request.getObjectKey().trim();
        String expectedPrefix = "couple-places/%s/%s/".formatted(placeId, user.getId());
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ảnh không thuộc người dùng hoặc địa điểm này");
        }
        if (photoRepository.countByPlaceIdAndStatus(placeId, CouplePlaceStatus.PUBLISHED) >= MAX_PHOTOS_PER_PLACE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Địa điểm đã có tối đa 5 ảnh");
        }
        String contentType = verifyPhotoObject(objectKey);
        CouplePlacePhoto photo = new CouplePlacePhoto();
        photo.setId(sequenceService.next("couple_place_photos"));
        photo.setPlaceId(placeId);
        photo.setUserId(user.getId());
        photo.setUserName(user.getName());
        photo.setObjectKey(objectKey);
        photo.setUrl(photoPublicUrl(objectKey));
        photo.setContentType(contentType);
        photo.setStatus(CouplePlaceStatus.PUBLISHED);
        return photoRepository.save(photo);
    }

    private void ensurePhotoUploadEnabled() {
        if (!photoUploadEnabled) {
            throw new ResponseStatusException(HttpStatus.GONE, "Tính năng tải ảnh đang tạm tắt");
        }
        if (mediaBucket == null || mediaBucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình kho ảnh địa điểm");
        }
    }

    private String verifyPhotoObject(String objectKey) {
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(mediaBucket)
                    .key(objectKey)
                    .build());
            String contentType = normalizePhotoContentType(head.contentType());
            validatePhotoLength(head.contentLength());
            return contentType;
        } catch (NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa tìm thấy ảnh đã tải lên");
        } catch (S3Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xác thực ảnh đã tải lên");
        }
    }

    private String normalizePhotoContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PHOTO_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP");
        }
        return normalized;
    }

    private void validatePhotoLength(Long contentLength) {
        if (contentLength == null || contentLength <= 0 || contentLength > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh địa điểm phải nhỏ hơn 5MB");
        }
    }

    private String photoPublicUrl(String objectKey) {
        String base = mediaPublicBaseUrl == null ? "" : mediaPublicBaseUrl.trim();
        if (!base.isBlank()) {
            return base.replaceAll("/+$", "") + "/" + objectKey;
        }
        if (mediaBucket.contains(".")) {
            return "https://s3.%s.amazonaws.com/%s/%s".formatted(mediaRegion, mediaBucket, objectKey);
        }
        if ("us-east-1".equals(mediaRegion)) {
            return "https://%s.s3.amazonaws.com/%s".formatted(mediaBucket, objectKey);
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(mediaBucket, mediaRegion, objectKey);
    }

    private String photoExtension(String contentType, String fileName) {
        String cleaned = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (cleaned.endsWith(".jpg") || cleaned.endsWith(".jpeg")) return ".jpg";
        if (cleaned.endsWith(".png")) return ".png";
        if (cleaned.endsWith(".webp")) return ".webp";
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public List<AdminCouplePlaceResponse> adminPlaces() {
        List<CouplePlace> places = placeRepository.findByStatusIn(List.of(CouplePlaceStatus.PUBLISHED, CouplePlaceStatus.HIDDEN, CouplePlaceStatus.ARCHIVED));
        List<CouplePlace> publicPlaces = places.stream()
                .filter(place -> effectiveVisibility(place) == CouplePlaceVisibility.PUBLIC)
                .collect(Collectors.toCollection(ArrayList::new));
        enrichAll(publicPlaces, null, false);
        return places.stream()
                .map(place -> {
                    boolean metadataOnly = effectiveVisibility(place) == CouplePlaceVisibility.COUPLE_PRIVATE;
                    return AdminCouplePlaceResponse.from(place, metadataOnly);
                })
                .sorted(Comparator.comparing(AdminCouplePlaceResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Map<String, Object> adminReviews(Long placeId, int page, int limit, CouplePlaceStatus status) {
        CouplePlace place = requirePublicAdminPlace(placeId);
        int safePage = Math.max(0, page);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        PageRequest pageable = PageRequest.of(safePage, safeLimit);
        Page<CouplePlaceReview> reviews = status == null
                ? reviewRepository.findByPlaceIdOrderByCreatedAtDesc(place.getId(), pageable)
                : reviewRepository.findByPlaceIdAndStatusOrderByCreatedAtDesc(place.getId(), status, pageable);
        return Map.of(
                "items", reviews.getContent(),
                "page", safePage,
                "limit", safeLimit,
                "total", reviews.getTotalElements(),
                "hasMore", reviews.hasNext()
        );
    }

    public CouplePlaceReview updateReviewStatus(Long placeId, Long reviewId, CouplePlaceStatus status) {
        CouplePlace place = requirePublicAdminPlace(placeId);
        if (status != CouplePlaceStatus.PUBLISHED && status != CouplePlaceStatus.HIDDEN) {
            throw new IllegalArgumentException("Trang thai review khong hop le");
        }
        CouplePlaceReview review = reviewRepository.findByIdAndPlaceId(reviewId, placeId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay review"));
        review.setStatus(status);
        CouplePlaceReview saved = reviewRepository.save(review);
        recalculateStats(place);
        placeRepository.save(place);
        return saved;
    }

    public void deleteReview(Long placeId, Long reviewId) {
        CouplePlace place = requirePublicAdminPlace(placeId);
        CouplePlaceReview review = reviewRepository.findByIdAndPlaceId(reviewId, placeId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay review"));
        reviewRepository.delete(review);
        recalculateStats(place);
        placeRepository.save(place);
    }

    public List<CouplePlaceReport> adminReports() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(CouplePlaceReportStatus.OPEN).stream()
                .filter(report -> placeRepository.findById(report.getPlaceId())
                        .map(place -> effectiveVisibility(place) == CouplePlaceVisibility.PUBLIC)
                        .orElse(false))
                .toList();
    }

    public CouplePlace updateStatus(Long placeId, CouplePlaceStatus status) {
        CouplePlace place = getExistingPlace(placeId);
        place.setStatus(status);
        return placeRepository.save(place);
    }

    public CouplePlace updatePlace(Long id, com.hi.api.dto.request.UpdateCouplePlaceRequest request) {
        CouplePlace place = getExistingPlace(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            place.setName(request.getName());
        }
        if (request.getDescription() != null) {
            place.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            place.setCategory(request.getCategory());
        }
        if (request.getAddress() != null) {
            if (place.getLocation() == null) {
                place.setLocation(new com.hi.api.model.CouplePlace.Location());
            }
            place.getLocation().setAddress(request.getAddress());
        }
        return enrich(placeRepository.save(place), null, false);
    }

    public void deletePlace(Long id) {
        CouplePlace place = getExistingPlace(id);
        placeRepository.delete(place);
    }


    private CouplePlace enrich(CouplePlace place, User user, boolean includeDetails) {
        if (place == null || place.getId() == null) {
            return place;
        }
        enrichAll(List.of(place), user, includeDetails);
        return place;
    }

    private void enrichAll(List<CouplePlace> places, User user, boolean includeDetails) {
        List<Long> placeIds = places.stream()
                .map(CouplePlace::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (placeIds.isEmpty()) {
            return;
        }

        Map<Long, Set<CouplePlaceReactionType>> reactionsByPlace = user != null && user.getId() != null
                ? reactionRepository.findByPlaceIdInAndUserId(placeIds, user.getId()).stream()
                        .collect(Collectors.groupingBy(
                                CouplePlaceReaction::getPlaceId,
                                Collectors.mapping(CouplePlaceReaction::getType, Collectors.toSet())
                        ))
                : Map.of();
        Map<Long, List<CouplePlacePhoto>> photosByPlace = photoRepository.findByPlaceIdInAndStatusOrderByCreatedAtDesc(placeIds, CouplePlaceStatus.PUBLISHED).stream()
                .collect(Collectors.groupingBy(CouplePlacePhoto::getPlaceId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<CouplePlaceReview>> reviewsByPlace = reviewRepository.findByPlaceIdInAndStatusOrderByCreatedAtDesc(placeIds, CouplePlaceStatus.PUBLISHED).stream()
                .collect(Collectors.groupingBy(CouplePlaceReview::getPlaceId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> reviewedPlaceIds = user != null && user.getId() != null
                ? reviewRepository.findByPlaceIdInAndUserId(placeIds, user.getId()).stream()
                        .map(CouplePlaceReview::getPlaceId)
                        .collect(Collectors.toSet())
                : Set.of();
        int reviewLimit = includeDetails ? 20 : 3;

        for (CouplePlace place : places) {
            if (place.getId() == null) {
                continue;
            }
            Set<CouplePlaceReactionType> userReactions = reactionsByPlace.getOrDefault(place.getId(), Set.of());
            place.setLikedByMe(userReactions.contains(CouplePlaceReactionType.LIKE));
            place.setDislikedByMe(userReactions.contains(CouplePlaceReactionType.DISLIKE));
            place.setSavedByMe(userReactions.contains(CouplePlaceReactionType.SAVE));
            place.setOwnedByMe(user != null && user.getId() != null && user.getId().equals(place.getCreatedBy()));
            place.setReviewedByMe(reviewedPlaceIds.contains(place.getId()));
            place.setVisibility(effectiveVisibility(place));
            place.setPhotos(photosByPlace.getOrDefault(place.getId(), List.of()).stream().limit(MAX_PHOTOS_PER_PLACE).toList());
            place.setRecentReviews(reviewsByPlace.getOrDefault(place.getId(), List.of()).stream().limit(reviewLimit).toList());
        }
    }

    private List<CouplePlace> fetchOsmNearby(double lat, double lng, int radius, CouplePlaceCategory category) {
        String cacheKey = cacheKey(lat, lng, radius, category);
        Optional<GooglePlaceCache> cached = googleCacheRepository.findById(cacheKey)
                .filter(cache -> cache.getExpiresAt() != null && cache.getExpiresAt().isAfter(Instant.now()));
        if (cached.isPresent()) {
            return cached.get().getPlaces().stream().map(this::fromCachedOsmPlace).toList();
        }

        List<GooglePlaceCache.CachedGooglePlace> places = requestOsmNearby(lat, lng, radius, category);
        GooglePlaceCache cache = new GooglePlaceCache();
        cache.setCacheKey(cacheKey);
        cache.setLat(roundCoordinate(lat));
        cache.setLng(roundCoordinate(lng));
        cache.setRadius(radiusBucket(radius));
        cache.setCategory(category);
        cache.setPlaces(places);
        cache.setExpiresAt(Instant.now().plus(Duration.ofHours(Math.max(1, osmCacheTtlHours))));
        googleCacheRepository.save(cache);
        return places.stream().map(this::fromCachedOsmPlace).toList();
    }

    @SuppressWarnings("unchecked")
    private List<GooglePlaceCache.CachedGooglePlace> requestOsmNearby(double lat, double lng, int radius, CouplePlaceCategory category) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    overpassUrl,
                    new HttpEntity<>(overpassQuery(lat, lng, radius, category), headers),
                    Map.class
            );
            Object rawElements = response.getBody() != null ? response.getBody().get("elements") : null;
            if (!(rawElements instanceof List<?> list)) {
                return List.of();
            }
            List<GooglePlaceCache.CachedGooglePlace> result = new ArrayList<>();
            for (Object rawElement : list) {
                if (rawElement instanceof Map<?, ?> elementMap) {
                    GooglePlaceCache.CachedGooglePlace item = toCachedOsmPlace((Map<String, Object>) elementMap);
                    if (item.getGooglePlaceId() != null && item.getLat() != null && item.getLng() != null) {
                        result.add(item);
                    }
                }
                if (result.size() >= MAX_OSM_RESULTS) {
                    break;
                }
            }
            return result;
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private GooglePlaceCache.CachedGooglePlace toCachedOsmPlace(Map<String, Object> placeMap) {
        GooglePlaceCache.CachedGooglePlace item = new GooglePlaceCache.CachedGooglePlace();
        String type = asString(placeMap.get("type"));
        String id = asString(placeMap.get("id"));
        item.setGooglePlaceId("osm:" + type + ":" + id);

        Object rawTags = placeMap.get("tags");
        Map<String, Object> tags = rawTags instanceof Map<?, ?> tagMap ? (Map<String, Object>) tagMap : Map.of();
        String name = firstNonBlank(asString(tags.get("name")), asString(tags.get("brand")), asString(tags.get("operator")));
        item.setName(name.isBlank() ? defaultOsmName(tags) : name);
        item.setAddress(osmAddress(tags));

        item.setLat(asDouble(placeMap.get("lat")));
        item.setLng(asDouble(placeMap.get("lon")));
        Object center = placeMap.get("center");
        if ((item.getLat() == null || item.getLng() == null) && center instanceof Map<?, ?> centerMap) {
            item.setLat(asDouble(centerMap.get("lat")));
            item.setLng(asDouble(centerMap.get("lon")));
        }

        List<String> types = new ArrayList<>();
        for (String key : List.of("amenity", "tourism", "leisure", "shop")) {
            String value = asString(tags.get(key));
            if (!value.isBlank()) {
                types.add(value);
            }
        }
        item.setTypes(types);
        return item;
    }

    private CouplePlace fromCachedOsmPlace(GooglePlaceCache.CachedGooglePlace cached) {
        CouplePlace place = new CouplePlace();
        place.setName(cached.getName());
        place.setDescription("Dia diem cong khai tu OpenStreetMap");
        place.setCategory(categoryFromOsmTypes(cached.getTypes()));
        CouplePlace.Location location = new CouplePlace.Location();
        location.setLat(cached.getLat());
        location.setLng(cached.getLng());
        location.setAddress(cached.getAddress());
        place.setLocation(location);
        place.setSource(CouplePlaceSource.OSM);
        place.setGooglePlaceId(cached.getGooglePlaceId());
        place.setStatus(CouplePlaceStatus.PUBLISHED);
        place.setTags(cached.getTypes());
        return place;
    }

    private Comparator<CouplePlace> comparator(String sort) {
        String safeSort = sort == null ? "recommended" : sort.toLowerCase(Locale.ROOT);
        if ("distance".equals(safeSort)) {
            return Comparator.comparing(CouplePlace::getDistanceMeters, Comparator.nullsLast(Double::compareTo));
        }
        if ("rating".equals(safeSort)) {
            return Comparator.comparingDouble(this::ratingScore).reversed()
                    .thenComparing(CouplePlace::getDistanceMeters, Comparator.nullsLast(Double::compareTo));
        }
        if ("popular".equals(safeSort)) {
            return Comparator.comparingDouble(this::popularScore).reversed()
                    .thenComparing(CouplePlace::getDistanceMeters, Comparator.nullsLast(Double::compareTo));
        }
        return Comparator.comparingDouble(this::recommendedScore).reversed()
                .thenComparing(CouplePlace::getDistanceMeters, Comparator.nullsLast(Double::compareTo));
    }

    private double recommendedScore(CouplePlace place) {
        double distanceBoost = place.getDistanceMeters() == null ? 0 : Math.max(0, 20 - place.getDistanceMeters() / 500.0);
        return ratingScore(place) * 10 + popularScore(place) + distanceBoost;
    }

    private double ratingScore(CouplePlace place) {
        double userRating = place.getUserRatingAvg() != null && place.getUserRatingAvg() > 0 ? place.getUserRatingAvg() : 0;
        double googleRating = place.getGoogleRating() != null ? place.getGoogleRating() : 0;
        return Math.max(userRating, googleRating);
    }

    private double popularScore(CouplePlace place) {
        int externalCount = place.getGoogleUserRatingCount() != null ? Math.min(place.getGoogleUserRatingCount(), 500) : 0;
        return externalCount * 0.03
                + safeInt(place.getLikeCount()) * 2.0
                - safeInt(place.getDislikeCount()) * 2.0
                + safeInt(place.getSaveCount()) * 2.0
                + safeInt(place.getReviewCount()) * 3.0;
    }

    private void recalculateStats(CouplePlace place) {
        long reviewCount = reviewRepository.countByPlaceIdAndStatus(place.getId(), CouplePlaceStatus.PUBLISHED);
        List<CouplePlaceReview> reviews = reviewRepository.findByPlaceIdAndStatusOrderByCreatedAtDesc(place.getId(), CouplePlaceStatus.PUBLISHED);
        double avg = reviews.stream().map(CouplePlaceReview::getRating).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0.0);
        place.setReviewCount((int) reviewCount);
        place.setUserRatingAvg(Math.round(avg * 10.0) / 10.0);
        place.setLikeCount((int) reactionRepository.countByPlaceIdAndType(place.getId(), CouplePlaceReactionType.LIKE));
        place.setDislikeCount((int) reactionRepository.countByPlaceIdAndType(place.getId(), CouplePlaceReactionType.DISLIKE));
        place.setSaveCount((int) reactionRepository.countByPlaceIdAndType(place.getId(), CouplePlaceReactionType.SAVE));
        place.setReportCount((int) reportRepository.countByPlaceIdAndStatus(place.getId(), CouplePlaceReportStatus.OPEN));
    }

    private CouplePlace getExistingPlace(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Dia diem khong hop le");
        }
        return placeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Khong tim thay dia diem"));
    }

    private CouplePlace getAccessiblePlace(User user, Long id) {
        CouplePlace place = getExistingPlace(id);
        if (!canView(user, place)) {
            throw new AccessDeniedException("Ban khong co quyen xem dia diem nay");
        }
        return place;
    }

    private CouplePlace requirePublicAdminPlace(Long id) {
        CouplePlace place = getExistingPlace(id);
        if (effectiveVisibility(place) != CouplePlaceVisibility.PUBLIC) {
            throw new AccessDeniedException("Review private khong duoc hien thi trong admin");
        }
        return place;
    }

    private boolean canView(User user, CouplePlace place) {
        if (effectiveVisibility(place) == CouplePlaceVisibility.PUBLIC) return true;
        if (user == null || user.getId() == null) return false;
        if (user.getId().equals(place.getCreatedBy())) return true;
        if (place.getPrivateMemberIds() == null || !place.getPrivateMemberIds().contains(user.getId())) return false;
        return partnerAccessService.isActivePair(place.getCreatedBy(), user.getId());
    }

    private CouplePlaceVisibility effectiveVisibility(CouplePlace place) {
        return place.getVisibility() == null ? CouplePlaceVisibility.PUBLIC : place.getVisibility();
    }

    private Optional<CouplePlace> findReusablePlace(String googlePlaceId,
                                                     CouplePlaceVisibility visibility,
                                                     String pairKey) {
        String cleanedGooglePlaceId = clean(googlePlaceId);
        if (cleanedGooglePlaceId.isBlank()) return Optional.empty();
        return Optional.ofNullable(placeRepository.findAllByGooglePlaceId(cleanedGooglePlaceId))
                .orElseGet(List::of)
                .stream()
                .filter(place -> effectiveVisibility(place) == visibility)
                .filter(place -> visibility == CouplePlaceVisibility.PUBLIC || Objects.equals(place.getPairKey(), pairKey))
                .findFirst();
    }

    private void validateCoordinates(Double lat, Double lng) {
        if (!isValidCoordinate(lat, lng)) {
            throw new IllegalArgumentException("Toa do khong hop le");
        }
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private int safeRadius(Integer radius) {
        if (radius == null) {
            return DEFAULT_RADIUS_METERS;
        }
        return Math.min(Math.max(radius, 100), MAX_RADIUS_METERS);
    }

    private Bounds boundsAround(double lat, double lng, int radiusMeters) {
        double latDelta = radiusMeters / 111_320.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double lngDelta = Math.abs(cosLat) < 0.01 ? 180.0 : radiusMeters / (111_320.0 * Math.abs(cosLat));
        return new Bounds(
                Math.max(-90.0, lat - latDelta),
                Math.min(90.0, lat + latDelta),
                Math.max(-180.0, lng - lngDelta),
                Math.min(180.0, lng + lngDelta)
        );
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String cacheKey(double lat, double lng, int radius, CouplePlaceCategory category) {
        return "osm:" + roundCoordinate(lat) + ":" + roundCoordinate(lng) + ":" + radiusBucket(radius) + ":" + (category == null ? "ALL" : category.name());
    }

    private int radiusBucket(int radius) {
        return Math.max(1000, ((radius + 999) / 1000) * 1000);
    }

    private double roundCoordinate(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Bounds(double south, double north, double west, double east) {}

    private String overpassQuery(double lat, double lng, int radius, CouplePlaceCategory category) {
        List<String> filters = osmFilters(category);
        StringBuilder builder = new StringBuilder("[out:json][timeout:8];(");
        for (String filter : filters) {
            builder.append("node").append(filter).append("(around:")
                    .append(radius).append(",").append(lat).append(",").append(lng).append(");");
            builder.append("way").append(filter).append("(around:")
                    .append(radius).append(",").append(lat).append(",").append(lng).append(");");
            builder.append("relation").append(filter).append("(around:")
                    .append(radius).append(",").append(lat).append(",").append(lng).append(");");
        }
        builder.append(");out center ").append(MAX_OSM_RESULTS).append(";");
        return builder.toString();
    }

    private List<String> osmFilters(CouplePlaceCategory category) {
        if (category == null) {
            return List.of(
                    "[amenity~\"restaurant|cafe|bar|fast_food|cinema|karaoke_box|bowling_alley\"]",
                    "[leisure~\"park|amusement_arcade\"]",
                    "[tourism~\"attraction|gallery|museum\"]",
                    "[shop~\"mall|department_store|supermarket\"]"
            );
        }
        return switch (category) {
            case FOOD -> List.of("[amenity~\"restaurant|fast_food|food_court\"]");
            case CAFE -> List.of("[amenity=\"cafe\"]");
            case CINEMA -> List.of("[amenity=\"cinema\"]");
            case PARK -> List.of("[leisure=\"park\"]");
            case SHOPPING -> List.of("[shop~\"mall|department_store|supermarket\"]");
            case ENTERTAINMENT -> List.of("[amenity~\"karaoke_box|bowling_alley\"]", "[leisure=\"amusement_arcade\"]");
            case DATE_SPOT -> List.of("[tourism~\"attraction|gallery|museum\"]", "[amenity~\"restaurant|cafe\"]", "[leisure=\"park\"]");
            case OTHER -> List.of("[amenity]", "[tourism]", "[leisure]", "[shop]");
        };
    }

    private CouplePlaceCategory categoryFromOsmTypes(List<String> types) {
        if (types == null) {
            return CouplePlaceCategory.OTHER;
        }
        if (types.contains("cafe")) return CouplePlaceCategory.CAFE;
        if (types.contains("restaurant") || types.contains("food")) return CouplePlaceCategory.FOOD;
        if (types.contains("cinema")) return CouplePlaceCategory.CINEMA;
        if (types.contains("park")) return CouplePlaceCategory.PARK;
        if (types.contains("mall") || types.contains("supermarket") || types.contains("department_store")) return CouplePlaceCategory.SHOPPING;
        if (types.contains("amusement_arcade") || types.contains("karaoke_box") || types.contains("bowling_alley")) return CouplePlaceCategory.ENTERTAINMENT;
        if (types.contains("attraction") || types.contains("gallery") || types.contains("museum")) return CouplePlaceCategory.DATE_SPOT;
        return CouplePlaceCategory.OTHER;
    }

    private String defaultOsmName(Map<String, Object> tags) {
        CouplePlaceCategory category = categoryFromOsmTypes(List.of(
                asString(tags.get("amenity")),
                asString(tags.get("tourism")),
                asString(tags.get("leisure")),
                asString(tags.get("shop"))
        ));
        return switch (category) {
            case FOOD -> "Dia diem an uong";
            case CAFE -> "Cafe gan ban";
            case DATE_SPOT -> "Diem hen ho";
            case ENTERTAINMENT -> "Dia diem vui choi";
            case CINEMA -> "Rap phim";
            case PARK -> "Cong vien";
            case SHOPPING -> "Dia diem mua sam";
            case OTHER -> "Dia diem gan ban";
        };
    }

    private String osmAddress(Map<String, Object> tags) {
        List<String> parts = List.of(
                asString(tags.get("addr:housenumber")),
                asString(tags.get("addr:street")),
                asString(tags.get("addr:suburb")),
                asString(tags.get("addr:district")),
                asString(tags.get("addr:city"))
        ).stream().filter(value -> value != null && !value.isBlank()).toList();
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        return firstNonBlank(
                asString(tags.get("addr:full")),
                asString(tags.get("addr:place")),
                asString(tags.get("description"))
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String collapseSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String removeDiacritics(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replace('đ', 'd').replace('Đ', 'D');
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream().map(this::clean).filter(value -> !value.isBlank()).distinct().limit(12).toList();
    }

    private String displayName(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return "Hi Lover";
        }
        return user.getName();
    }

    private String displayContributorName(User user, Boolean anonymous, String nickname) {
        if (Boolean.TRUE.equals(anonymous)) {
            return "Ẩn danh";
        }
        String cleanedNickname = clean(nickname);
        if (!cleanedNickname.isBlank()) {
            return cleanedNickname.length() > 40 ? cleanedNickname.substring(0, 40) : cleanedNickname;
        }
        return displayName(user);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String extensionFor(String contentType, String fileName) {
        String fallback = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String cleaned = clean(fileName).toLowerCase(Locale.ROOT);
        if (cleaned.endsWith(".png") || cleaned.endsWith(".jpg") || cleaned.endsWith(".jpeg") || cleaned.endsWith(".webp")) {
            int dot = cleaned.lastIndexOf('.');
            return cleaned.substring(dot);
        }
        return fallback;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
