package com.hi.api.repository;

import com.hi.api.model.CouplePlaceReview;
import com.hi.api.model.CouplePlaceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouplePlaceReviewRepository extends MongoRepository<CouplePlaceReview, Long> {
    List<CouplePlaceReview> findByPlaceIdAndStatusOrderByCreatedAtDesc(Long placeId, CouplePlaceStatus status);
    List<CouplePlaceReview> findByPlaceIdInAndStatusOrderByCreatedAtDesc(List<Long> placeIds, CouplePlaceStatus status);
    long countByPlaceIdAndStatus(Long placeId, CouplePlaceStatus status);
    Page<CouplePlaceReview> findByPlaceIdOrderByCreatedAtDesc(Long placeId, Pageable pageable);
    Page<CouplePlaceReview> findByPlaceIdAndStatusOrderByCreatedAtDesc(Long placeId, CouplePlaceStatus status, Pageable pageable);
    Optional<CouplePlaceReview> findByIdAndPlaceId(Long id, Long placeId);
}
