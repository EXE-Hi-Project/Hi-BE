package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "sale_campaigns")
public class SaleCampaign {
    @Id
    @JsonProperty("_id")
    private String id;
    private String name;
    private String title;
    private String subtitle;
    private Long hiProSalePrice;
    private Long hiMaxSalePrice;
    @Indexed
    private Instant startsAt;
    @Indexed
    private Instant endsAt;
    @Indexed
    private SaleCampaignStatus status = SaleCampaignStatus.DRAFT;
    private String createdBy;
    private String updatedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
