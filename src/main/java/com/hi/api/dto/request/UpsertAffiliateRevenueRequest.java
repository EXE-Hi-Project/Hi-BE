package com.hi.api.dto.request;

import com.hi.api.model.AffiliatePlatform;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
public class UpsertAffiliateRevenueRequest {
    private AffiliatePlatform platform;
    private String platformOrderId;
    private Long productId;
    private String userId;
    private Long clickTrackingId;
    private BigDecimal orderAmount;
    private BigDecimal commissionAmount;
    private String currency;
    private String status;
    private Instant orderedAt;
    private Instant settledAt;
    private Map<String, Object> sourcePayload;
}
