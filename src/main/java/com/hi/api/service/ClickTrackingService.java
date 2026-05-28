package com.hi.api.service;

import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.ClickTracking;
import com.hi.api.repository.AffiliateProductRepository;
import com.hi.api.repository.ClickTrackingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClickTrackingService {

    private final ClickTrackingRepository clickTrackingRepository;
    private final AffiliateProductRepository affiliateProductRepository;
    private final SequenceService sequenceService;

    public ClickTrackingService(ClickTrackingRepository clickTrackingRepository,
                                AffiliateProductRepository affiliateProductRepository,
                                SequenceService sequenceService) {
        this.clickTrackingRepository = clickTrackingRepository;
        this.affiliateProductRepository = affiliateProductRepository;
        this.sequenceService = sequenceService;
    }

    public Map<String, Object> trackClick(String userId, Long productId) {
        AffiliateProduct product = affiliateProductRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm affiliate"));

        ClickTracking tracking = new ClickTracking();
        tracking.setId(sequenceService.next("click_trackings"));
        tracking.setUserId(userId);
        tracking.setProductId(productId);
        tracking.setClickedAt(LocalDateTime.now());
        clickTrackingRepository.save(tracking);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tracking", tracking);
        result.put("affiliateUrl", product.getAffiliateUrl());
        result.put("product", product);
        return result;
    }
}