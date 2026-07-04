package com.hi.api.service;

import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlaceReaction;
import com.hi.api.model.CouplePlaceReactionType;
import com.hi.api.model.CouplePlaceReview;
import com.hi.api.model.CouplePlaceStatus;
import com.hi.api.model.CouplePlaceVisibility;
import com.hi.api.model.User;
import com.hi.api.dto.request.CreateCouplePlaceRequest;
import com.hi.api.repository.CouplePlacePhotoRepository;
import com.hi.api.repository.CouplePlaceReactionRepository;
import com.hi.api.repository.CouplePlaceReportRepository;
import com.hi.api.repository.CouplePlaceRepository;
import com.hi.api.repository.CouplePlaceReviewRepository;
import com.hi.api.repository.GooglePlaceCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouplePlaceServiceTest {
    private CouplePlaceService service;
    private RestTemplate restTemplate;
    private CouplePlaceRepository placeRepository;
    private CouplePlaceReactionRepository reactionRepository;
    private CouplePlaceReviewRepository reviewRepository;
    private SequenceService sequenceService;
    private PartnerAccessService partnerAccessService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        placeRepository = mock(CouplePlaceRepository.class);
        reactionRepository = mock(CouplePlaceReactionRepository.class);
        reviewRepository = mock(CouplePlaceReviewRepository.class);
        sequenceService = mock(SequenceService.class);
        partnerAccessService = mock(PartnerAccessService.class);
        service = new CouplePlaceService(
                placeRepository,
                reviewRepository,
                reactionRepository,
                mock(CouplePlaceReportRepository.class),
                mock(CouplePlacePhotoRepository.class),
                mock(GooglePlaceCacheRepository.class),
                sequenceService,
                restTemplate,
                partnerAccessService
        );
        ReflectionTestUtils.setField(service, "photonUrl", "https://photon.komoot.io/api");
        ReflectionTestUtils.setField(service, "tomTomSearchApiKey", "");
        ReflectionTestUtils.setField(service, "tomTomSearchUrl", "https://api.tomtom.com/search/2/search");
    }

    @Test
    void searchAddressUsesGeneralPhotonAutocompleteWithVietnamAndLocationBias() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("features", List.of(
                        photonFeature(
                                "Chợ Bến Thành",
                                "Công trường Quách Thị Trang",
                                "",
                                "Bến Thành",
                                "Thành phố Hồ Chí Minh",
                                "marketplace",
                                106.6980365,
                                10.7725301
                        )
                ))));

        List<Map<String, Object>> suggestions = service.searchAddress("Chợ Bến Thành", 10.8231, 106.6297);

        assertFalse(suggestions.isEmpty());
        assertEquals("Chợ Bến Thành, Công trường Quách Thị Trang, Bến Thành, Thành phố Hồ Chí Minh, Việt Nam",
                suggestions.get(0).get("displayName"));
        assertEquals("marketplace", suggestions.get(0).get("type"));
        assertEquals("PHOTON", suggestions.get(0).get("source"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1))
                .exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        String requestedUrl = URLDecoder.decode(urlCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(requestedUrl.contains("q=Chợ Bến Thành"));
        assertTrue(requestedUrl.contains("countrycode=VN"));
        assertTrue(requestedUrl.contains("lat=10.8231"));
        assertTrue(requestedUrl.contains("lon=106.6297"));
        assertFalse(requestedUrl.contains("bbox="));
    }

    @Test
    void searchAddressSupportsVietnameseHouseAndAlleyAddresses() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("features", List.of(
                        photonFeature(
                                "Hẻm 763 Kha Vạn Cân",
                                "Kha Vạn Cân",
                                "763/60",
                                "Linh Xuân",
                                "Thành phố Hồ Chí Minh",
                                "residential",
                                106.7546603,
                                10.8553601
                        )
                ))));

        List<Map<String, Object>> suggestions = service.searchAddress("763/60 Kha Vạn Cân", 10.85, 106.75);

        assertFalse(suggestions.isEmpty());
        assertEquals("Hẻm 763 Kha Vạn Cân, 763/60 Kha Vạn Cân, Linh Xuân, Thành phố Hồ Chí Minh, Việt Nam",
                suggestions.get(0).get("displayName"));
        assertEquals(106.7546603, suggestions.get(0).get("lng"));
        assertEquals(10.8553601, suggestions.get(0).get("lat"));
        assertEquals(asText(suggestions.get(0).get("displayName")).split(",")[0], suggestions.get(0).get("name"));
    }

    @Test
    void searchAddressKeepsMultipleTomTomBranchesWithTheSameName() {
        ReflectionTestUtils.setField(service, "tomTomSearchApiKey", "test-key");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenAnswer(invocation -> {
                    String url = URLDecoder.decode(invocation.getArgument(0), StandardCharsets.UTF_8);
                    if (url.contains("api.tomtom.com")) {
                        return ResponseEntity.ok(Map.of("results", List.of(
                                tomTomResult("kokoria-su-van-hanh", "Kokoria", "Sư Vạn Hạnh, Quận 10, Thành phố Hồ Chí Minh", 106.6670, 10.7729),
                                tomTomResult("kokoria-phan-van-tri", "Kokoria", "Phan Văn Trị, Gò Vấp, Thành phố Hồ Chí Minh", 106.6840, 10.8290),
                                tomTomResult("kokoria-le-van-duyet", "Kokoria", "Lê Văn Duyệt, Bình Thạnh, Thành phố Hồ Chí Minh", 106.6964, 10.7968),
                                tomTomResult("kokoria-xo-viet-nghe-tinh", "Kokoria", "Xô Viết Nghệ Tĩnh, Bình Thạnh, Thành phố Hồ Chí Minh", 106.7111, 10.7991)
                        )));
                    }
                    return ResponseEntity.ok(Map.of("features", List.of(
                            photonFeature(
                                    "Kokoria",
                                    "Lê Văn Duyệt",
                                    "50/1A",
                                    "Gia Định",
                                    "Thành phố Hồ Chí Minh",
                                    "restaurant",
                                    106.6963928,
                                    10.7968442
                            )
                    )));
                });

        List<Map<String, Object>> suggestions = service.searchAddress("kokoria", 10.8231, 106.6297);

        assertEquals(4, suggestions.size());
        assertTrue(suggestions.stream().allMatch(item -> asText(item.get("displayName")).startsWith("Kokoria")));
        assertTrue(suggestions.stream().allMatch(item -> "Kokoria".equals(item.get("name"))));
        assertTrue(suggestions.stream().allMatch(item -> "TOMTOM".equals(item.get("source"))));
        assertEquals(4, suggestions.stream().map(item -> item.get("id")).distinct().count());
        assertTrue(suggestions.stream().anyMatch(item -> asText(item.get("displayName")).contains("Sư Vạn Hạnh")));
        assertTrue(suggestions.stream().anyMatch(item -> asText(item.get("displayName")).contains("Phan Văn Trị")));
        assertTrue(suggestions.stream().anyMatch(item -> asText(item.get("displayName")).contains("Lê Văn Duyệt")));
        assertTrue(suggestions.stream().anyMatch(item -> asText(item.get("displayName")).contains("Xô Viết Nghệ Tĩnh")));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(2))
                .exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        String tomTomUrl = urlCaptor.getAllValues().stream()
                .map(url -> URLDecoder.decode(url, StandardCharsets.UTF_8))
                .filter(url -> url.contains("api.tomtom.com"))
                .findFirst()
                .orElseThrow();
        assertTrue(tomTomUrl.contains("/search/2/search/kokoria.json"));
        assertFalse(tomTomUrl.contains("apiVersion=1"));
        assertTrue(tomTomUrl.contains("lat=10.8231"));
        assertTrue(tomTomUrl.contains("lon=106.6297"));
    }

    @Test
    void searchAddressMapsTomTomPoiNameSeparatelyFromAddress() {
        ReflectionTestUtils.setField(service, "tomTomSearchApiKey", "test-key");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    if (url.contains("api.tomtom.com")) {
                        return ResponseEntity.ok(Map.of("results", List.of(
                                tomTomResult("manwah-giga", "Manwah Taiwanese Hotpot", "Giga Mall, Thá»§ Äá»©c, ThÃ nh phá»‘ Há»“ ChÃ­ Minh", 106.7218, 10.8277)
                        )));
                    }
                    return ResponseEntity.ok(Map.of("features", List.of()));
                });

        List<Map<String, Object>> suggestions = service.searchAddress("manwah giga", 10.8231, 106.7290);

        assertFalse(suggestions.isEmpty());
        assertEquals("Manwah Taiwanese Hotpot", suggestions.get(0).get("name"));
        assertTrue(asText(suggestions.get(0).get("address")).contains("Giga Mall"));
        assertEquals("TOMTOM", suggestions.get(0).get("source"));
    }

    @Test
    void dislikeRemovesExistingLikeAndCreatesDislike() {
        CouplePlace place = new CouplePlace();
        place.setId(1L);
        place.setName("Test place");
        CouplePlaceReaction like = new CouplePlaceReaction();
        like.setId(10L);
        like.setPlaceId(1L);
        like.setUserId("user-1");
        like.setType(CouplePlaceReactionType.LIKE);
        User user = new User();
        user.setId("user-1");

        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
        when(placeRepository.save(place)).thenReturn(place);
        when(reactionRepository.findByPlaceIdAndUserIdAndType(1L, "user-1", CouplePlaceReactionType.DISLIKE))
                .thenReturn(Optional.empty());
        when(reactionRepository.findByPlaceIdAndUserIdAndType(1L, "user-1", CouplePlaceReactionType.LIKE))
                .thenReturn(Optional.of(like));
        when(sequenceService.next("couple_place_reactions")).thenReturn(11L);

        service.setReaction(user, 1L, CouplePlaceReactionType.DISLIKE, true);

        verify(reactionRepository).delete(like);
        ArgumentCaptor<CouplePlaceReaction> reactionCaptor = ArgumentCaptor.forClass(CouplePlaceReaction.class);
        verify(reactionRepository).save(reactionCaptor.capture());
        assertEquals(CouplePlaceReactionType.DISLIKE, reactionCaptor.getValue().getType());
    }

    @Test
    void privatePlaceOwnerAlwaysHasAccessButOnlyOriginalActivePartnerCanView() {
        CouplePlace privatePlace = privatePlace(41L, "owner", "partner");
        User owner = user("owner", null);
        User partner = user("partner", "owner");
        User stranger = user("stranger", "owner");
        when(placeRepository.findById(41L)).thenReturn(Optional.of(privatePlace));

        assertEquals(41L, service.get(owner, 41L).getId());
        assertThrows(AccessDeniedException.class, () -> service.get(partner, 41L));
        assertThrows(AccessDeniedException.class, () -> service.get(stranger, 41L));

        when(partnerAccessService.isActivePair("owner", "partner")).thenReturn(true);
        assertEquals(41L, service.get(partner, 41L).getId());
    }

    @Test
    void savedPlacesExcludePrivatePlacesAfterPartnerDisconnects() {
        User formerPartner = user("partner", null);
        CouplePlace privatePlace = privatePlace(42L, "owner", "partner");
        CouplePlaceReaction saved = new CouplePlaceReaction();
        saved.setPlaceId(42L);
        saved.setUserId("partner");
        saved.setType(CouplePlaceReactionType.SAVE);
        when(reactionRepository.findByUserIdAndType("partner", CouplePlaceReactionType.SAVE)).thenReturn(List.of(saved));
        when(placeRepository.findById(42L)).thenReturn(Optional.of(privatePlace));
        when(partnerAccessService.isActivePair("owner", "partner")).thenReturn(false);

        assertTrue(service.savedPlaces(formerPartner).isEmpty());
    }

    @Test
    void privateProviderPlaceDoesNotReusePublicRecord() {
        User owner = user("owner", "partner");
        User partner = user("partner", "owner");
        CouplePlace publicPlace = new CouplePlace();
        publicPlace.setId(5L);
        publicPlace.setGooglePlaceId("provider-123");
        publicPlace.setVisibility(CouplePlaceVisibility.PUBLIC);
        when(partnerAccessService.requireCurrentPartner(owner)).thenReturn(partner);
        when(partnerAccessService.pairKey("owner", "partner")).thenReturn("owner:partner");
        when(placeRepository.findAllByGooglePlaceId("provider-123")).thenReturn(List.of(publicPlace));
        when(sequenceService.next("couple_places")).thenReturn(99L);
        when(placeRepository.save(any(CouplePlace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCouplePlaceRequest request = new CreateCouplePlaceRequest();
        request.setName("Private date place");
        request.setCategory(CouplePlaceCategory.DATE_SPOT);
        request.setLat(10.77);
        request.setLng(106.70);
        request.setGooglePlaceId("provider-123");
        request.setVisibility(CouplePlaceVisibility.COUPLE_PRIVATE);

        CouplePlace created = service.create(owner, request);

        assertEquals(99L, created.getId());
        assertEquals(CouplePlaceVisibility.COUPLE_PRIVATE, created.getVisibility());
        assertEquals("owner:partner", created.getPairKey());
        assertEquals(List.of("owner", "partner"), created.getPrivateMemberIds());
    }

    @Test
    void adminCannotReadPrivateReviewContent() {
        when(placeRepository.findById(43L)).thenReturn(Optional.of(privatePlace(43L, "owner", "partner")));

        assertThrows(AccessDeniedException.class, () -> service.adminReviews(43L, 0, 20, null));
    }

    @Test
    void hidingPublicReviewRecalculatesPublishedRating() {
        CouplePlace publicPlace = new CouplePlace();
        publicPlace.setId(44L);
        publicPlace.setVisibility(CouplePlaceVisibility.PUBLIC);
        CouplePlaceReview review = new CouplePlaceReview();
        review.setId(7L);
        review.setPlaceId(44L);
        review.setStatus(CouplePlaceStatus.PUBLISHED);
        when(placeRepository.findById(44L)).thenReturn(Optional.of(publicPlace));
        when(reviewRepository.findByIdAndPlaceId(7L, 44L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(reviewRepository.countByPlaceIdAndStatus(44L, CouplePlaceStatus.PUBLISHED)).thenReturn(0L);
        when(reviewRepository.findByPlaceIdAndStatusOrderByCreatedAtDesc(44L, CouplePlaceStatus.PUBLISHED)).thenReturn(List.of());

        CouplePlaceReview updated = service.updateReviewStatus(44L, 7L, CouplePlaceStatus.HIDDEN);

        assertEquals(CouplePlaceStatus.HIDDEN, updated.getStatus());
        assertEquals(0, publicPlace.getReviewCount());
        assertEquals(0.0, publicPlace.getUserRatingAvg());
        verify(placeRepository).save(publicPlace);
    }

    private CouplePlace privatePlace(Long id, String ownerId, String partnerId) {
        CouplePlace place = new CouplePlace();
        place.setId(id);
        place.setName("Private place");
        place.setCreatedBy(ownerId);
        place.setVisibility(CouplePlaceVisibility.COUPLE_PRIVATE);
        place.setPrivateMemberIds(List.of(ownerId, partnerId));
        place.setStatus(CouplePlaceStatus.PUBLISHED);
        return place;
    }

    private User user(String id, String partnerId) {
        User user = new User();
        user.setId(id);
        user.setPartnerId(partnerId);
        return user;
    }

    private Map<String, Object> tomTomResult(String id,
                                             String name,
                                             String address,
                                             double lng,
                                             double lat) {
        return Map.of(
                "id", id,
                "type", "POI",
                "score", 4.5,
                "dist", 1200,
                "poi", Map.of("name", name, "categories", List.of("restaurant")),
                "address", Map.of(
                        "freeformAddress", address,
                        "municipality", "Thành phố Hồ Chí Minh",
                        "country", "Việt Nam"
                ),
                "position", Map.of("lat", lat, "lon", lng)
        );
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> photonFeature(String name,
                                               String street,
                                               String houseNumber,
                                               String district,
                                               String city,
                                               String type,
                                               double lng,
                                               double lat) {
        return Map.of(
                "properties", Map.of(
                        "name", name,
                        "street", street,
                        "housenumber", houseNumber,
                        "district", district,
                        "city", city,
                        "country", "Việt Nam",
                        "osm_type", "N",
                        "osm_id", 12345,
                        "osm_key", "amenity",
                        "osm_value", type
                ),
                "geometry", Map.of(
                        "type", "Point",
                        "coordinates", List.of(lng, lat)
                )
        );
    }
}
