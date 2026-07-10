package com.hi.api.controller;

import com.hi.api.exception.GlobalExceptionHandler;
import com.hi.api.model.Transaction;
import com.hi.api.model.User;
import com.hi.api.service.AiDailyUsageService;
import com.hi.api.service.PaymentService;
import com.hi.api.service.SubscriptionAccessService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

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
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Vui lòng chọn gói thanh toán"
                ));
            }

            PaymentService.CheckoutSessionResult checkout = paymentService.createCheckoutSession(user, priceId, origin);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", checkout.activated()
                    ? "Đã kích hoạt gói Hi thành công"
                    : "Tạo phiên thanh toán thành công");
            response.put("data", checkout.toResponseData());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return logAndReturnInternalError("PAYMENT_CHECKOUT", e);
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Webhook webhook) {
        try {
            paymentService.handleWebhook(webhook);
            return ResponseEntity.ok("Received");
        } catch (Exception e) {
            String trackingId = UUID.randomUUID().toString();
            log.error("[PAYMENT_WEBHOOK:{}] Webhook handling failed", trackingId, e);
            return ResponseEntity.badRequest().body("Webhook Error");
        }
    }

    @GetMapping("/subscription")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> getSubscription(@AuthenticationPrincipal User user) {
        SubscriptionAccessService.SubscriptionAccess access = subscriptionAccessService.getAccess(user.getId());
        AiDailyUsageService.Usage usage = aiDailyUsageService.current(user.getId(), access.aiDailyLimit());
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
        data.put("couplePremium", subscriptionAccessService.hasPremiumForCouple(user));
        data.put("sharedFromPartner", access.sharedFromPartner());
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
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã dừng gia hạn gói Hi"));
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
            return logAndReturnInternalError("PAYMENT_HISTORY", e);
        }
    }

    private ResponseEntity<Map<String, Object>> logAndReturnInternalError(String code, Exception exception) {
        String trackingId = UUID.randomUUID().toString();
        log.error("[{}:{}] Payment request failed", code, trackingId, exception);
        return GlobalExceptionHandler.internalError(trackingId);
    }
}
