package com.hi.api.controller;

import com.hi.api.dto.request.AffiliateLinkPreviewRequest;
import com.hi.api.dto.request.SyncAffiliateProductsRequest;
import com.hi.api.dto.request.UpsertAffiliateProductRequest;
import com.hi.api.dto.request.UpsertAffiliateRevenueRequest;
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
            @RequestParam(required = false) String symptomCategory,
            @RequestParam(required = false) String phase,
            @RequestParam(defaultValue = "8") int limit) {
        List<AffiliateProduct> products = affiliateProductService.getRecommendations(symptomCategory, phase, limit);
        return ResponseEntity.ok(Map.of("success", true, "products", products));
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminOverview() {
        return ResponseEntity.ok(Map.of("success", true, "overview", affiliateProductService.getAdminOverview()));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sync(
            @Valid @RequestBody(required = false) SyncAffiliateProductsRequest req,
            @RequestParam(required = false) AffiliatePlatform platform) {
        if (platform == null && req != null) {
            platform = req.getPlatform();
        }
        Map<String, Object> result = affiliateProductService.sync(platform);
        return ResponseEntity.ok(Map.of("success", true, "result", result));
    }

    @PostMapping("/preview-link")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> previewLink(@Valid @RequestBody AffiliateLinkPreviewRequest req) {
        return ResponseEntity.ok(Map.of("success", true, "preview", affiliateProductService.previewLink(req.getUrl())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        AffiliateProduct product = affiliateProductService.getById(id);
        return ResponseEntity.ok(Map.of("success", true, "product", product));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody UpsertAffiliateProductRequest req) {
        AffiliateProduct product = affiliateProductService.createProduct(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "product", product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpsertAffiliateProductRequest req) {
        AffiliateProduct product = affiliateProductService.updateProduct(id, req);
        return ResponseEntity.ok(Map.of("success", true, "product", product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        affiliateProductService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã ẩn sản phẩm affiliate"));
    }

    @PostMapping("/revenue-events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upsertRevenue(@Valid @RequestBody UpsertAffiliateRevenueRequest req) {
        return ResponseEntity.ok(Map.of("success", true, "revenueEvent", affiliateProductService.upsertRevenue(req)));
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Map<String, Object>> trackClick(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Map<String, Object> result = clickTrackingService.trackClick(user != null ? user.getId() : null, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", result));
    }
}
