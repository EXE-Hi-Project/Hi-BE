package com.hi.api.controller;

import com.hi.api.dto.request.SyncAffiliateProductsRequest;
import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.User;
import com.hi.api.service.AffiliateProductService;
import com.hi.api.service.ClickTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/affiliate-products")
public class AffiliateProductController {

    private final AffiliateProductService affiliateProductService;
    private final ClickTrackingService clickTrackingService;

    public AffiliateProductController(AffiliateProductService affiliateProductService,
                                      ClickTrackingService clickTrackingService) {
        this.affiliateProductService = affiliateProductService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AffiliatePlatform platform,
            @RequestParam(required = false) String symptomCategory,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "20") int limit) {
        List<AffiliateProduct> products = affiliateProductService.searchProducts(q, platform, symptomCategory, active, limit);
        return ResponseEntity.ok(Map.of("success", true, "products", products));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> recommendations(
            @RequestParam String symptomCategory,
            @RequestParam(defaultValue = "8") int limit) {
        List<AffiliateProduct> products = affiliateProductService.getRecommendations(symptomCategory, limit);
        return ResponseEntity.ok(Map.of("success", true, "products", products));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sync(@Valid @RequestBody(required = false) SyncAffiliateProductsRequest req) {
        AffiliatePlatform platform = req != null && req.getPlatform() != null ? req.getPlatform() : AffiliatePlatform.TIKTOK;
        if (platform != AffiliatePlatform.TIKTOK) {
            throw new IllegalArgumentException("Hiện module này chỉ cấu hình sync TikTok Shop");
        }

        Map<String, Object> result = affiliateProductService.syncFromTiktok();
        return ResponseEntity.ok(Map.of("success", true, "syncResult", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        AffiliateProduct product = affiliateProductService.getById(id);
        return ResponseEntity.ok(Map.of("success", true, "product", product));
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Map<String, Object>> trackClick(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Map<String, Object> result = clickTrackingService.trackClick(user.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", result));
    }
}