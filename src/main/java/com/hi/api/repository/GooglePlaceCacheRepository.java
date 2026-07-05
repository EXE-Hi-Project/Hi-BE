package com.hi.api.repository;

import com.hi.api.model.GooglePlaceCache;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GooglePlaceCacheRepository extends MongoRepository<GooglePlaceCache, String> {
}
