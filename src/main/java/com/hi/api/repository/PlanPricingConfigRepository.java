package com.hi.api.repository;

import com.hi.api.model.PlanPricingConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlanPricingConfigRepository extends MongoRepository<PlanPricingConfig, String> {
}
