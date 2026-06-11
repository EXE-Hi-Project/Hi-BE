package com.hi.api.dto.request;

import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateCommissionSource;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpsertAffiliateProductRequest {
    private AffiliatePlatform platform;
    private String externalProductId;
    private String name;
    private String description;
    private BigDecimal price;
    private Double commissionRate;
    private BigDecimal commissionAmount;
    private AffiliateCommissionSource commissionSource;
    private String affiliateUrl;
    private String imageUrl;
    private String symptomCategory;
    private String category;
    private List<String> symptomTags;
    private List<String> phaseTags;
    private List<String> goalTags;
    private String audience;
    private String status;
    private Integer priority;
    private String sourceName;
    private String currency;
    private Boolean isActive;
}
