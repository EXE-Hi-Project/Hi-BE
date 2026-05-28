package com.hi.api.repository;

import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AffiliateProductRepository extends MongoRepository<AffiliateProduct, Long> {
    Optional<AffiliateProduct> findByPlatformAndExternalProductId(AffiliatePlatform platform, String externalProductId);
    List<AffiliateProduct> findByPlatformAndIsActiveTrueOrderByCommissionRateDescPriceAsc(AffiliatePlatform platform);
    List<AffiliateProduct> findBySymptomCategoryIgnoreCaseAndIsActiveTrueOrderByCommissionRateDescPriceAsc(String symptomCategory);
    List<AffiliateProduct> findByIsActiveTrueOrderByCommissionRateDescPriceAsc();
}