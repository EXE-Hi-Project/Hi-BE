package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "otp_deliveries")
public class OtpDelivery {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String purpose;
    private String status;
    @Indexed(unique = true, sparse = true)
    private String deliveryId;
    @Indexed(unique = true, sparse = true)
    private String providerMessageId;
    @Indexed(unique = true, sparse = true)
    private String lastWebhookId;
    private String reason;
    private Instant attemptedAt;
    private Instant statusUpdatedAt;
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;
}
