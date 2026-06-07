package com.hi.api.repository;

import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateRevenueEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AffiliateRevenueEventRepository extends MongoRepository<AffiliateRevenueEvent, Long> {
    Optional<AffiliateRevenueEvent> findByPlatformAndPlatformOrderId(AffiliatePlatform platform, String platformOrderId);
    List<AffiliateRevenueEvent> findTop50ByOrderByOrderedAtDesc();
}
