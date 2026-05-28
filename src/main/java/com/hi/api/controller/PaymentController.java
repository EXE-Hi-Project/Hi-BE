package com.hi.api.controller;

import com.hi.api.model.User;
import com.hi.api.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.payos.model.webhooks.Webhook;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-checkout-session")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> requestBody) {
        try {
            String priceId = requestBody.get("priceId");
            if (priceId == null || priceId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Price ID (planType) là bắt buộc"));
            }

            String checkoutUrl = paymentService.createCheckoutSession(user, priceId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Tạo phiên thanh toán thành công");
            response.put("data", Map.of("checkoutUrl", checkoutUrl));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi tạo phiên thanh toán: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Webhook webhook) {
        try {
            paymentService.handleWebhook(webhook);
            return ResponseEntity.ok("Received");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Webhook Error: " + e.getMessage());
        }
    }

    @GetMapping("/subscription")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> getSubscription(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Lấy thông tin subscription thành công");
        response.put("data", user.getSubscription());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> cancelSubscription(@AuthenticationPrincipal User user) {
        try {
            paymentService.cancelSubscription(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã hủy gia hạn gói Premium"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
