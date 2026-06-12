package com.hi.api.controller;

import com.hi.api.model.User;
import com.hi.api.model.Transaction;
import com.hi.api.service.PaymentService;
import com.hi.api.service.AiDailyUsageService;
import com.hi.api.service.SubscriptionAccessService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.payos.model.webhooks.Webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final AiDailyUsageService aiDailyUsageService;

    public PaymentController(PaymentService paymentService,
                             SubscriptionAccessService subscriptionAccessService,
                             AiDailyUsageService aiDailyUsageService) {
        this.paymentService = paymentService;
        this.subscriptionAccessService = subscriptionAccessService;
        this.aiDailyUsageService = aiDailyUsageService;
    }

    @PostMapping("/create-checkout-session")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> requestBody,
            @RequestHeader(value = "Origin", required = false) String origin) {
        try {
            String priceId = requestBody.get("priceId");
            if (priceId == null || priceId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Price ID (planType) là bắt buộc"));
            }

            String checkoutUrl = paymentService.createCheckoutSession(user, priceId, origin);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Tạo phiên thanh toán thành công");
            response.put("data", Map.of("checkoutUrl", checkoutUrl));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
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
        SubscriptionAccessService.SubscriptionAccess access = subscriptionAccessService.getAccess(user.getId());
        AiDailyUsageService.Usage usage = aiDailyUsageService.current(user.getId(), access.aiDailyLimit());
        boolean couplePremium = subscriptionAccessService.hasPremiumForCouple(user);
        User.SubscriptionInfo subscription = user.getSubscription() != null
                ? user.getSubscription()
                : new User.SubscriptionInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", access.plan());
        data.put("tier", access.tier());
        data.put("status", access.premium() ? "active" : subscription.getStatus());
        data.put("activeUntil", access.activeUntil());
        data.put("currentPeriodEnd", access.activeUntil());
        data.put("cancelAtPeriodEnd", access.cancelAtPeriodEnd());
        data.put("couplePremium", couplePremium);
        data.put("entitlements", subscriptionAccessService.getEffectiveEntitlements(user));
        data.put("aiUsage", usage);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Lấy thông tin subscription thành công");
        response.put("data", data);
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

    @GetMapping("/history")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> getPaymentHistory(@AuthenticationPrincipal User user) {
        try {
            List<Transaction> history = paymentService.getPaymentHistory(user);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Lấy lịch sử thanh toán thành công");
            response.put("data", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi lấy lịch sử thanh toán: " + e.getMessage()));
        }
    }
}

