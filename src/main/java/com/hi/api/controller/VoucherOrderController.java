package com.hi.api.controller;

import com.hi.api.dto.request.CreateVoucherCheckoutRequest;
import com.hi.api.exception.GlobalExceptionHandler;
import com.hi.api.model.User;
import com.hi.api.model.VoucherOrder;
import com.hi.api.service.VoucherOrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/voucher-orders")
@SecurityRequirement(name = "Bearer Authentication")
public class VoucherOrderController {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderController.class);

    private final VoucherOrderService voucherOrderService;

    public VoucherOrderController(VoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateVoucherCheckoutRequest req,
            @RequestHeader(value = "Origin", required = false) String origin) {
        try {
            Map<String, Object> result = voucherOrderService.createCheckout(user, req, origin);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return logAndReturnInternalError("VOUCHER_CHECKOUT", ex);
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@AuthenticationPrincipal User user) {
        List<VoucherOrder> orders = voucherOrderService.getMyOrders(user);
        return ResponseEntity.ok(Map.of("success", true, "data", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", voucherOrderService.getOwnedOrder(user, id)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/resend-email")
    public ResponseEntity<Map<String, Object>> resendEmail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", voucherOrderService.resendEmail(user, id)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> logAndReturnInternalError(String code, Exception exception) {
        String trackingId = UUID.randomUUID().toString();
        log.error("[{}:{}] Voucher request failed", code, trackingId, exception);
        return GlobalExceptionHandler.internalError(trackingId);
    }
}
