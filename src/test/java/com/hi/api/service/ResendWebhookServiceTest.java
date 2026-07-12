package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.repository.ResendWebhookReceiptRepository;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResendWebhookServiceTest {
    private static final String SECRET = "whsec_" + "dGVzdC1zZWNyZXQ=";

    @Test
    void verifiedDeliveryEventUpdatesOtpDelivery() throws Exception {
        OtpDeliveryService deliveries = mock(OtpDeliveryService.class);
        ResendWebhookReceiptRepository receipts = mock(ResendWebhookReceiptRepository.class);
        when(receipts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResendWebhookService service = new ResendWebhookService(new ObjectMapper(), deliveries, receipts, SECRET);
        String payload = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"provider-1\",\"tags\":[{\"name\":\"hi_delivery_id\",\"value\":\"delivery-1\"}]}}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signature("event-1", timestamp, payload);

        service.handle(payload, "event-1", timestamp, signature);

        verify(deliveries).applyEvent("delivery-1", "provider-1", "event-1", "DELIVERED", null);
        verify(receipts).save(any());
    }

    @Test
    void invalidSignatureIsRejectedBeforeAnyPersistence() {
        OtpDeliveryService deliveries = mock(OtpDeliveryService.class);
        ResendWebhookReceiptRepository receipts = mock(ResendWebhookReceiptRepository.class);
        ResendWebhookService service = new ResendWebhookService(new ObjectMapper(), deliveries, receipts, SECRET);

        assertThrows(IllegalArgumentException.class,
                () -> service.handle("{}", "event-1", String.valueOf(Instant.now().getEpochSecond()), "v1,invalid"));

        verifyNoInteractions(deliveries, receipts);
    }

    private String signature(String id, String timestamp, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring(6)), "HmacSHA256"));
        byte[] signature = mac.doFinal((id + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }
}
