package com.hi.api.repository;

import com.hi.api.model.AnalyticsEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyticsEventRepository extends MongoRepository<AnalyticsEvent, String> {
    List<AnalyticsEvent> findBySessionId(String sessionId);
    long countByEventType(String eventType);
}
