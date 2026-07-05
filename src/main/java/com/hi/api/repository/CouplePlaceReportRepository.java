package com.hi.api.repository;

import com.hi.api.model.CouplePlaceReport;
import com.hi.api.model.CouplePlaceReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CouplePlaceReportRepository extends MongoRepository<CouplePlaceReport, Long> {
    Optional<CouplePlaceReport> findByPlaceIdAndUserIdAndStatus(Long placeId, String userId, CouplePlaceReportStatus status);
    long countByPlaceIdAndStatus(Long placeId, CouplePlaceReportStatus status);
    List<CouplePlaceReport> findByStatusOrderByCreatedAtDesc(CouplePlaceReportStatus status);
}
