package com.hi.api.controller;

import com.hi.api.dto.request.ConfirmCouplePlacePhotoRequest;
import com.hi.api.dto.request.CreateCouplePlaceRequest;
import com.hi.api.dto.request.CreateCouplePlaceReviewRequest;
import com.hi.api.dto.request.PresignCouplePlacePhotoRequest;
import com.hi.api.dto.request.ReportCouplePlaceRequest;
import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlacePhoto;
import com.hi.api.model.CouplePlaceReactionType;
import com.hi.api.model.CouplePlaceReport;
import com.hi.api.model.CouplePlaceReview;
import com.hi.api.model.User;
import com.hi.api.service.CouplePlaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/couple-places")
public class CouplePlaceController {

    private final CouplePlaceService couplePlaceService;

    public CouplePlaceController(CouplePlaceService couplePlaceService) {
        this.couplePlaceService = couplePlaceService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> nearby(
            @AuthenticationPrincipal User user,
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) CouplePlaceCategory category,
            @RequestParam(defaultValue = "recommended") String sort) {
        List<CouplePlace> places = couplePlaceService.nearby(user, lat, lng, radius, category, sort);
        return ResponseEntity.ok(Map.of("success", true, "places", places));
    }

    @GetMapping("/saved")
    public ResponseEntity<Map<String, Object>> saved(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("success", true, "places", couplePlaceService.savedPlaces(user)));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @AuthenticationPrincipal User user,
            @RequestParam String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(Map.of("success", true, "suggestions", couplePlaceService.searchAddress(user, q, lat, lng)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.get(user, id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCouplePlaceRequest request) {
        CouplePlace place = couplePlaceService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "place", place));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<Map<String, Object>> review(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody CreateCouplePlaceReviewRequest request) {
        CouplePlaceReview review = couplePlaceService.addReview(user, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "review", review));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> like(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.LIKE, true)));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> unlike(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.LIKE, false)));
    }

    @PostMapping("/{id}/dislike")
    public ResponseEntity<Map<String, Object>> dislike(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.DISLIKE, true)));
    }

    @DeleteMapping("/{id}/dislike")
    public ResponseEntity<Map<String, Object>> undislike(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.DISLIKE, false)));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> save(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.SAVE, true)));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> unsave(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "place", couplePlaceService.setReaction(user, id, CouplePlaceReactionType.SAVE, false)));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<Map<String, Object>> report(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ReportCouplePlaceRequest request) {
        CouplePlaceReport report = couplePlaceService.report(user, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "report", report));
    }

    @PostMapping("/{id}/photos/presign")
    public ResponseEntity<Map<String, Object>> presignPhoto(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody PresignCouplePlacePhotoRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "data", couplePlaceService.presignPhoto(user, id, request)));
    }

    @PostMapping("/{id}/photos/confirm")
    public ResponseEntity<Map<String, Object>> confirmPhoto(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmCouplePlacePhotoRequest request) {
        CouplePlacePhoto photo = couplePlaceService.confirmPhoto(user, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "photo", photo));
    }
}
