package com.hi.api.controller;

import com.hi.api.service.PlanPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/plans")
public class PlanPricingController {
    private final PlanPricingService pricingService;

    public PlanPricingController(PlanPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/pricing")
    public ResponseEntity<Map<String, Object>> pricing() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", pricingService.getPublicPricing()
        ));
    }
}
