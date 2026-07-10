package com.hi.api.repository;

import com.hi.api.model.CouplePlacePhoto;
import com.hi.api.model.CouplePlaceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CouplePlacePhotoRepository extends MongoRepository<CouplePlacePhoto, Long> {
    List<CouplePlacePhoto> findByPlaceIdAndStatusOrderByCreatedAtDesc(Long placeId, CouplePlaceStatus status);
    List<CouplePlacePhoto> findByPlaceIdInAndStatusOrderByCreatedAtDesc(List<Long> placeIds, CouplePlaceStatus status);
    long countByPlaceIdAndStatus(Long placeId, CouplePlaceStatus status);
}
