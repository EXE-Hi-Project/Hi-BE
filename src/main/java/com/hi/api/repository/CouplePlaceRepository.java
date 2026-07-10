package com.hi.api.repository;

import com.hi.api.model.CouplePlace;
import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlaceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CouplePlaceRepository extends MongoRepository<CouplePlace, Long> {
    List<CouplePlace> findByStatus(CouplePlaceStatus status);
    List<CouplePlace> findByStatusIn(List<CouplePlaceStatus> statuses);
    Optional<CouplePlace> findByGooglePlaceId(String googlePlaceId);
    List<CouplePlace> findAllByGooglePlaceId(String googlePlaceId);

    @Query("{ 'status': ?0, 'location.lat': { $gte: ?2, $lte: ?3 }, 'location.lng': { $gte: ?4, $lte: ?5 }, $or: [ { 'visibility': 'PUBLIC' }, { 'visibility': null }, { 'visibility': { $exists: false } }, { 'privateMemberIds': ?1 } ] }")
    List<CouplePlace> findVisibleWithinBounds(CouplePlaceStatus status,
                                               String userId,
                                               double south,
                                               double north,
                                               double west,
                                               double east);

    @Query("{ 'status': ?0, 'category': ?1, 'location.lat': { $gte: ?3, $lte: ?4 }, 'location.lng': { $gte: ?5, $lte: ?6 }, $or: [ { 'visibility': 'PUBLIC' }, { 'visibility': null }, { 'visibility': { $exists: false } }, { 'privateMemberIds': ?2 } ] }")
    List<CouplePlace> findVisibleWithinBoundsAndCategory(CouplePlaceStatus status,
                                                          CouplePlaceCategory category,
                                                          String userId,
                                                          double south,
                                                          double north,
                                                          double west,
                                                          double east);
}
