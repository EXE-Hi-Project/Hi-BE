package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.OtpDelivery;
import com.hi.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ResendOtpEmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendOtpEmailService.class);
    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OtpDeliveryService deliveryService;
    private final String apiKey;
    private final String fromEmail;

    public ResendOtpEmailService(RestTemplate restTemplate, ObjectMapper objectMapper,
                                 OtpDeliveryService deliveryService,
                                 @Value("${app.resend.api-key:}") String apiKey,
                                 @Value("${app.resend.from-email:}") String fromEmail) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.deliveryService = deliveryService;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public void send(User user, String purpose, String subject, String html) {
        if (apiKey == null || apiKey.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("OTP email delivery is not configured");
        }

        OtpDelivery delivery = deliveryService.begin(user.getId(), purpose);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", "otp/" + delivery.getDeliveryId());
            Map<String, Object> payload = Map.of(
                    "from", fromEmail,
                    "to", List.of(user.getEmail()),
                    "subject", subject,
                    "html", html,
                    "tags", List.of(
                            Map.of("name", "hi_delivery_id", "value", delivery.getDeliveryId()),
                            Map.of("name", "hi_purpose", "value", purpose)
                    )
            );
            String response = restTemplate.postForObject(RESEND_EMAILS_URL, new HttpEntity<>(payload, headers), String.class);
            JsonNode responseJson = objectMapper.readTree(response == null ? "{}" : response);
            String providerMessageId = responseJson.path("id").asText();
            if (providerMessageId.isBlank()) throw new IllegalStateException("Resend did not return an email ID");
            deliveryService.markSent(delivery, providerMessageId);
            log.info("[OTP-RESEND:{}] accepted purpose={}", delivery.getDeliveryId(), purpose);
        } catch (Exception exception) {
            deliveryService.markFailed(delivery);
            log.error("[OTP-RESEND:{}] send failed purpose={}", delivery.getDeliveryId(), purpose, exception);
            throw new IllegalStateException("Resend OTP delivery failed", exception);
        }
    }
}
