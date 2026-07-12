package com.hi.api.service;

import com.hi.api.model.OtpDelivery;
import com.hi.api.repository.OtpDeliveryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OtpDeliveryService {
    private final OtpDeliveryRepository repository;

    public OtpDeliveryService(OtpDeliveryRepository repository) { this.repository = repository; }

    public OtpDelivery begin(String userId, String purpose) {
        Instant now = Instant.now();
        OtpDelivery delivery = new OtpDelivery();
        delivery.setUserId(userId);
        delivery.setPurpose(purpose);
        delivery.setStatus("PENDING");
        delivery.setDeliveryId(UUID.randomUUID().toString());
        delivery.setAttemptedAt(now);
        delivery.setStatusUpdatedAt(now);
        delivery.setExpiresAt(now.plus(90, ChronoUnit.DAYS));
        return repository.save(delivery);
    }

    public void markSent(OtpDelivery delivery, String providerMessageId) {
        update(delivery, "SENT", providerMessageId, null, null);
    }

    public void markFailed(OtpDelivery delivery) { update(delivery, "FAILED", null, "Không thể gửi OTP", null); }

    public void applyEvent(String deliveryId, String providerMessageId, String webhookId, String status, String reason) {
        OtpDelivery delivery = deliveryId == null ? null : repository.findByDeliveryId(deliveryId).orElse(null);
        if (delivery == null && providerMessageId != null) delivery = repository.findByProviderMessageId(providerMessageId).orElse(null);
        if (delivery == null) return;
        update(delivery, status, providerMessageId, reason, webhookId);
    }

    public List<OtpDelivery> history(String userId) { return repository.findTop10ByUserIdOrderByAttemptedAtDesc(userId); }

    public Map<String, OtpDelivery> latestByUserIds(Collection<String> userIds) {
        Map<String, OtpDelivery> latest = new HashMap<>();
        for (OtpDelivery delivery : repository.findByUserIdInOrderByAttemptedAtDesc(userIds)) {
            latest.putIfAbsent(delivery.getUserId(), delivery);
        }
        return latest;
    }

    private void update(OtpDelivery delivery, String status, String providerMessageId, String reason, String webhookId) {
        delivery.setStatus(status);
        if (providerMessageId != null && !providerMessageId.isBlank()) delivery.setProviderMessageId(providerMessageId);
        if (reason != null) delivery.setReason(reason);
        if (webhookId != null) delivery.setLastWebhookId(webhookId);
        delivery.setStatusUpdatedAt(Instant.now());
        repository.save(delivery);
    }
}
