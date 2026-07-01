package com.hi.api.repository;

import com.hi.api.model.AiCostLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiCostLogRepository extends MongoRepository<AiCostLog, String> {
    Optional<AiCostLog> findByMonth(String month);
}
