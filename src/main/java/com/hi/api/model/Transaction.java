package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "transactions")
@CompoundIndex(
        name = "transaction_one_pending_subscription_user_idx",
        def = "{'userId': 1}",
        unique = true,
        partialFilter = "{'type': 'SUBSCRIPTION', 'status': 'pending'}"
)
public class Transaction {

    @Id
    @JsonProperty("_id")
    private String id;

    private String userId;
    private String userEmail;
    private String type;

    @Indexed(unique = true)
    private Long orderCode;

    private Long amount;
    private Long baseAmount;
    private Long paidAmount;
    private String campaignId;
    private String planDisplayName;
    private String plan;
    private String status; // pending, completed, failed, refunded, canceled
    private String description;
    private String checkoutUrl;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
