package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "affiliate_revenue_events")
@CompoundIndexes({
        @CompoundIndex(name = "affiliate_revenue_platform_order_idx", def = "{ 'platform': 1, 'platformOrderId': 1 }", unique = true),
        @CompoundIndex(name = "affiliate_revenue_product_ordered_idx", def = "{ 'productId': 1, 'orderedAt': -1 }")
})
public class AffiliateRevenueEvent {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private AffiliatePlatform platform;

    @Indexed
    private String platformOrderId;

    @Indexed
    private Long productId;

    private String userId;

    private Long clickTrackingId;

    private BigDecimal orderAmount = BigDecimal.ZERO;

    private BigDecimal commissionAmount = BigDecimal.ZERO;

    private String currency = "VND";

    @Indexed
    private String status = "PENDING";

    private Instant orderedAt;

    private Instant settledAt;

    private Map<String, Object> sourcePayload;

    @CreatedDate
    private Instant createdAt;
}
