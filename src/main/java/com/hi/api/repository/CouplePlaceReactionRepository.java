package com.hi.api.repository;

import com.hi.api.model.CouplePlaceReaction;
import com.hi.api.model.CouplePlaceReactionType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CouplePlaceReactionRepository extends MongoRepository<CouplePlaceReaction, Long> {
    Optional<CouplePlaceReaction> findByPlaceIdAndUserIdAndType(Long placeId, String userId, CouplePlaceReactionType type);
    boolean existsByPlaceIdAndUserIdAndType(Long placeId, String userId, CouplePlaceReactionType type);
    long countByPlaceIdAndType(Long placeId, CouplePlaceReactionType type);
    List<CouplePlaceReaction> findByUserIdAndType(String userId, CouplePlaceReactionType type);
}
