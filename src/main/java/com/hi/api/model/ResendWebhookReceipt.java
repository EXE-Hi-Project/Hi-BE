package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "resend_webhook_receipts")
public class ResendWebhookReceipt {
    @Id
    private String id;
    @Indexed(unique = true)
    private String webhookId;
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;
}
