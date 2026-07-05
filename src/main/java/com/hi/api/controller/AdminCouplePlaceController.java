package com.hi.api.controller;

import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceReport;
import com.hi.api.model.CouplePlaceReview;
import com.hi.api.model.CouplePlaceStatus;
import com.hi.api.model.CouplePlaceVisibility;
import com.hi.api.dto.response.AdminCouplePlaceResponse;
import com.hi.api.service.CouplePlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/couple-places")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouplePlaceController {

    private final CouplePlaceService couplePlaceService;

    public AdminCouplePlaceController(CouplePlaceService couplePlaceService) {
        this.couplePlaceService = couplePlaceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> places() {
        List<AdminCouplePlaceResponse> places = couplePlaceService.adminPlaces();
        return ResponseEntity.ok(Map.of("success", true, "places", places));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<Map<String, Object>> reviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) CouplePlaceStatus status) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "reviews", couplePlaceService.adminReviews(id, page, limit, status)
        ));
    }

    @PatchMapping("/{placeId}/reviews/{reviewId}/status")
    public ResponseEntity<Map<String, Object>> updateReviewStatus(
            @PathVariable Long placeId,
            @PathVariable Long reviewId,
            @RequestParam CouplePlaceStatus status) {
        CouplePlaceReview review = couplePlaceService.updateReviewStatus(placeId, reviewId, status);
        return ResponseEntity.ok(Map.of("success", true, "review", review));
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> reports() {
        List<CouplePlaceReport> reports = couplePlaceService.adminReports();
        return ResponseEntity.ok(Map.of("success", true, "reports", reports));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam CouplePlaceStatus status) {
        CouplePlace place = couplePlaceService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("success", true, "place", adminResponse(place)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @Valid @RequestBody com.hi.api.dto.request.UpdateCouplePlaceRequest request) {
        CouplePlace place = couplePlaceService.updatePlace(id, request);
        return ResponseEntity.ok(Map.of("success", true, "place", adminResponse(place)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        couplePlaceService.deletePlace(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private AdminCouplePlaceResponse adminResponse(CouplePlace place) {
        boolean metadataOnly = place.getVisibility() == CouplePlaceVisibility.COUPLE_PRIVATE;
        return AdminCouplePlaceResponse.from(place, metadataOnly);
    }
}
