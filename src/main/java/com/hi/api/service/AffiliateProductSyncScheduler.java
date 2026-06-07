package com.hi.api.service;

import com.hi.api.model.AffiliatePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AffiliateProductSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AffiliateProductSyncScheduler.class);

    private final AffiliateProductService affiliateProductService;

    @Value("${app.affiliate.sync.enabled:false}")
    private boolean enabled;

    public AffiliateProductSyncScheduler(AffiliateProductService affiliateProductService) {
        this.affiliateProductService = affiliateProductService;
    }

    @Scheduled(cron = "${app.affiliate.sync.cron:0 0 */6 * * *}")
    public void syncAffiliateProducts() {
        if (!enabled) {
            return;
        }

        for (AffiliatePlatform platform : new AffiliatePlatform[]{AffiliatePlatform.TIKTOK, AffiliatePlatform.SHOPEE}) {
            try {
                affiliateProductService.sync(platform);
                log.info("Affiliate product sync completed for {}", platform);
            } catch (Exception ex) {
                log.warn("Affiliate product sync failed for {}: {}", platform, ex.getMessage());
            }
        }
    }
}
