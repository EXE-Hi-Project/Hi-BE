package com.hi.api.controller;

import com.hi.api.dto.request.UpdatePlanPricingRequest;
import com.hi.api.dto.request.UpsertSaleCampaignRequest;
import com.hi.api.model.User;
import com.hi.api.service.PlanPricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {
    private final PlanPricingService pricingService;

    public AdminPricingController(PlanPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/plans/pricing")
    public ResponseEntity<Map<String, Object>> pricing() {
        return ok(pricingService.getAdminPricing());
    }

    @PutMapping("/plans/pricing")
    public ResponseEntity<Map<String, Object>> updatePricing(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody UpdatePlanPricingRequest request,
            HttpServletRequest servletRequest) {
        return ok(pricingService.updatePricing(admin.getId(), request, servletRequest.getRemoteAddr()));
    }

    @GetMapping("/sales")
    public ResponseEntity<Map<String, Object>> sales() {
        return ok(pricingService.listSales());
    }

    @PostMapping("/sales")
    public ResponseEntity<Map<String, Object>> createSale(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody UpsertSaleCampaignRequest request,
            HttpServletRequest servletRequest) {
        return ok(pricingService.createSale(admin.getId(), request, servletRequest.getRemoteAddr()));
    }

    @PutMapping("/sales/{saleId}")
    public ResponseEntity<Map<String, Object>> updateSale(
            @AuthenticationPrincipal User admin,
            @PathVariable String saleId,
            @Valid @RequestBody UpsertSaleCampaignRequest request,
            HttpServletRequest servletRequest) {
        return ok(pricingService.updateSale(admin.getId(), saleId, request, servletRequest.getRemoteAddr()));
    }

    @PostMapping("/sales/{saleId}/activate")
    public ResponseEntity<Map<String, Object>> activateSale(
            @AuthenticationPrincipal User admin,
            @PathVariable String saleId,
            HttpServletRequest request) {
        return ok(pricingService.activateSale(admin.getId(), saleId, request.getRemoteAddr()));
    }

    @PostMapping("/sales/{saleId}/disable")
    public ResponseEntity<Map<String, Object>> disableSale(
            @AuthenticationPrincipal User admin,
            @PathVariable String saleId,
            HttpServletRequest request) {
        return ok(pricingService.disableSale(admin.getId(), saleId, request.getRemoteAddr()));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
