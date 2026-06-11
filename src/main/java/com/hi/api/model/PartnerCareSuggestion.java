package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "partner_care_suggestions")
@CompoundIndex(name = "partner_care_recipient_date_idx", def = "{ 'recipientUserId': 1, 'suggestionDate': 1 }", unique = true)
public class PartnerCareSuggestion {

    @Id
    @JsonProperty("_id")
    private String id;

    private String pairKey;
    private String recipientUserId;
    private String partnerUserId;
    private LocalDate suggestionDate;
    private String sourceType;
    private Integer priority;
    private String reason;
    private String action;
    private String messageTemplate;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
