package com.hi.api.controller;

import com.hi.api.dto.request.CreateVoucherCheckoutRequest;
import com.hi.api.dto.request.GoogleAuthRequest;
import com.hi.api.exception.GlobalExceptionHandler;
import com.hi.api.model.User;
import com.hi.api.service.AiDailyUsageService;
import com.hi.api.service.AuthRateLimitService;
import com.hi.api.service.AuthService;
import com.hi.api.service.PaymentService;
import com.hi.api.service.SubscriptionAccessService;
import com.hi.api.service.VoucherOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SanitizedErrorResponseTest {

    @Test
    void globalHandlerDoesNotExposeUnexpectedExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(
                new RuntimeException("MongoTimeoutException: connection string leaked"));

        assertSanitizedInternalError(response, "MongoTimeoutException");
    }

    @Test
    void googleAuthDoesNotExposeProviderExceptionMessage() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.googleAuth(any(GoogleAuthRequest.class)))
                .thenThrow(new RuntimeException("Google OAuth stack trace with client_secret"));

        AuthController controller = new AuthController(authService, mock(AuthRateLimitService.class));

        ResponseEntity<Map<String, Object>> response = controller.googleAuth(new GoogleAuthRequest());

        assertSanitizedInternalError(response, "client_secret");
    }

    @Test
    void paymentCheckoutDoesNotExposeProviderExceptionMessage() throws Exception {
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.createCheckoutSession(any(User.class), anyString(), any()))
                .thenThrow(new RuntimeException("PayOS signature exception"));

        PaymentController controller = new PaymentController(
                paymentService,
                mock(SubscriptionAccessService.class),
                mock(AiDailyUsageService.class));

        ResponseEntity<Map<String, Object>> response = controller.createCheckoutSession(
                new User(),
                Map.of("priceId", "monthly"),
                "https://hilover.space");

        assertSanitizedInternalError(response, "PayOS");
    }

    @Test
    void voucherCheckoutDoesNotExposeProviderExceptionMessage() throws Exception {
        VoucherOrderService voucherOrderService = mock(VoucherOrderService.class);
        when(voucherOrderService.createCheckout(any(User.class), any(CreateVoucherCheckoutRequest.class), any()))
                .thenThrow(new IllegalStateException("SMTP voucher email failure"));

        VoucherOrderController controller = new VoucherOrderController(voucherOrderService);

        ResponseEntity<Map<String, Object>> response = controller.checkout(
                new User(),
                new CreateVoucherCheckoutRequest(),
                "https://hilover.space");

        assertSanitizedInternalError(response, "SMTP");
    }

    @SuppressWarnings("unchecked")
    private void assertSanitizedInternalError(ResponseEntity<Map<String, Object>> response, String forbiddenText) {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        String message = String.valueOf(body.get("message"));
        assertEquals("Hệ thống đang bận. Vui lòng thử lại sau.", message);
        assertFalse(body.toString().contains(forbiddenText), body.toString());

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data.get("trackingId"));
        assertEquals("INTERNAL_ERROR", data.get("code"));
    }
}
