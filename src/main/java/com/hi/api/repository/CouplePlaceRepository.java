package com.hi.api.repository;

import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CouplePlaceRepository extends MongoRepository<CouplePlace, Long> {
    List<CouplePlace> findByStatus(CouplePlaceStatus status);
    List<CouplePlace> findByStatusIn(List<CouplePlaceStatus> statuses);
    Optional<CouplePlace> findByGooglePlaceId(String googlePlaceId);
    List<CouplePlace> findAllByGooglePlaceId(String googlePlaceId);
}
