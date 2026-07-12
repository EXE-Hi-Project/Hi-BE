package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.OtpDelivery;
import com.hi.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResendOtpEmailServiceTest {
    @Test
    void acceptedSendStoresProviderEmailId() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OtpDeliveryService deliveries = mock(OtpDeliveryService.class);
        OtpDelivery delivery = new OtpDelivery();
        delivery.setDeliveryId("delivery-1");
        when(deliveries.begin("user-1", "ACTIVATION")).thenReturn(delivery);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"id\":\"resend-email-1\"}");
        ResendOtpEmailService service = new ResendOtpEmailService(restTemplate, new ObjectMapper(), deliveries,
                "re_test", "Hi Lover <no-reply@hilover.space>");

        service.send(user(), "ACTIVATION", "Subject", "<p>OTP</p>");

        verify(deliveries).markSent(delivery, "resend-email-1");
    }

    @Test
    void rejectedSendMarksDeliveryFailedWithoutExposingProviderError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OtpDeliveryService deliveries = mock(OtpDeliveryService.class);
        OtpDelivery delivery = new OtpDelivery();
        delivery.setDeliveryId("delivery-1");
        when(deliveries.begin("user-1", "ACTIVATION")).thenReturn(delivery);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new IllegalStateException("provider internal detail"));
        ResendOtpEmailService service = new ResendOtpEmailService(restTemplate, new ObjectMapper(), deliveries,
                "re_test", "Hi Lover <no-reply@hilover.space>");

        assertThrows(IllegalStateException.class, () -> service.send(user(), "ACTIVATION", "Subject", "<p>OTP</p>"));

        verify(deliveries).markFailed(delivery);
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        return user;
    }
}
