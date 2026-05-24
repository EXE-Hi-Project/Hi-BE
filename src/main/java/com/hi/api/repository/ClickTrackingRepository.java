package com.hi.api.repository;

import com.hi.api.model.ClickTracking;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickTrackingRepository extends MongoRepository<ClickTracking, Long> {
    long countByProductId(Long productId);
    long countByUserId(String userId);
    List<ClickTracking> findByProductIdOrderByClickedAtDesc(Long productId);
    List<ClickTracking> findTop20ByOrderByClickedAtDesc();
    long countByClickedAtBetween(LocalDateTime start, LocalDateTime end);
}