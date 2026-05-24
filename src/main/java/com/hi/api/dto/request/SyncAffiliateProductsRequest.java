package com.hi.api.dto.request;

import com.hi.api.model.AffiliatePlatform;
import lombok.Data;

@Data
public class SyncAffiliateProductsRequest {
    private AffiliatePlatform platform = AffiliatePlatform.TIKTOK;
}