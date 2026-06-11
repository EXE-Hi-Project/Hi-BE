package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "affiliate_products")
@CompoundIndexes({
        @CompoundIndex(name = "affiliate_product_platform_external_idx", def = "{ 'platform': 1, 'externalProductId': 1 }", unique = true),
        @CompoundIndex(name = "affiliate_product_category_active_idx", def = "{ 'symptomCategory': 1, 'isActive': 1 }")
})
public class AffiliateProduct {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private AffiliatePlatform platform;

    @Indexed
    private String externalProductId;

    private String name;

    private String description = "";

    private BigDecimal price = BigDecimal.ZERO;

    private Double commissionRate = 0.0;

    private BigDecimal commissionAmount = BigDecimal.ZERO;

    private AffiliateCommissionSource commissionSource = AffiliateCommissionSource.MANUAL;

    private String affiliateUrl = "";

    private String imageUrl = "";

    @Indexed
    private String symptomCategory = "";

    private String category = "";

    private List<String> symptomTags = new ArrayList<>();

    private List<String> phaseTags = new ArrayList<>();

    private List<String> goalTags = new ArrayList<>();

    private String audience = "BOTH";

    private String status = "ACTIVE";

    private Integer priority = 0;

    private String sourceName = "";

    private String currency = "VND";

    @Indexed
    private Boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant lastSyncedAt;
}
