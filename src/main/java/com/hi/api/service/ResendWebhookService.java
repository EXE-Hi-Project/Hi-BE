package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.ResendWebhookReceipt;
import com.hi.api.repository.ResendWebhookReceiptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class ResendWebhookService {
    private static final long MAX_TIMESTAMP_AGE_SECONDS = 300;
    private final ObjectMapper objectMapper;
    private final OtpDeliveryService deliveryService;
    private final ResendWebhookReceiptRepository receiptRepository;
    private final String webhookSecret;

    public ResendWebhookService(ObjectMapper objectMapper, OtpDeliveryService deliveryService,
                                ResendWebhookReceiptRepository receiptRepository,
                                @Value("${app.resend.webhook-secret:}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.deliveryService = deliveryService;
        this.receiptRepository = receiptRepository;
        this.webhookSecret = webhookSecret;
    }

    public void handle(String payload, String webhookId, String timestamp, String signature) {
        if (!isValidSignature(payload, webhookId, timestamp, signature)) throw new IllegalArgumentException("Invalid webhook");
        if (!recordReceipt(webhookId)) return;
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.path("data");
            String eventType = root.path("type").asText();
            String deliveryId = tagValue(data.path("tags"), "hi_delivery_id");
            String providerMessageId = data.path("email_id").asText(null);
            String status = switch (eventType) {
                case "email.sent" -> "SENT";
                case "email.delivered" -> "DELIVERED";
                case "email.bounced" -> "BOUNCED";
                case "email.complained" -> "COMPLAINED";
                case "email.delivery_delayed" -> "DELAYED";
                default -> null;
            };
            if (status != null) deliveryService.applyEvent(deliveryId, providerMessageId, webhookId, status, safeReason(eventType));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhook");
        }
    }

    private boolean recordReceipt(String webhookId) {
        try {
            ResendWebhookReceipt receipt = new ResendWebhookReceipt();
            receipt.setWebhookId(webhookId);
            receipt.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
            receiptRepository.save(receipt);
            return true;
        } catch (DuplicateKeyException exception) { return false; }
    }

    private boolean isValidSignature(String payload, String webhookId, String timestamp, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || webhookId == null || timestamp == null || signature == null) return false;
        try {
            long sentAt = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - sentAt) > MAX_TIMESTAMP_AGE_SECONDS) return false;
            String encodedSecret = webhookSecret.startsWith("whsec_") ? webhookSecret.substring(6) : webhookSecret;
            byte[] secret = decode(encodedSecret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((webhookId + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            for (String part : signature.split("\\s+")) {
                String[] pieces = part.split(",", 2);
                if (pieces.length == 2 && "v1".equals(pieces[0]) && MessageDigest.isEqual(expected, Base64.getDecoder().decode(pieces[1]))) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private byte[] decode(String value) {
        try { return Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException exception) { return Base64.getUrlDecoder().decode(value); }
    }

    private String tagValue(JsonNode tags, String expectedName) {
        if (!tags.isArray()) return null;
        for (JsonNode tag : tags) if (expectedName.equals(tag.path("name").asText())) return tag.path("value").asText(null);
        return null;
    }

    private String safeReason(String eventType) {
        return switch (eventType) {
            case "email.bounced" -> "Email bị trả lại bởi nhà cung cấp nhận";
            case "email.complained" -> "Người nhận báo cáo email không mong muốn";
            case "email.delivery_delayed" -> "Nhà cung cấp đang trì hoãn giao email";
            default -> null;
        };
    }
}
