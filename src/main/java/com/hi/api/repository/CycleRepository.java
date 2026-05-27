package com.hi.api.repository;

import com.hi.api.model.Cycle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CycleRepository extends MongoRepository<Cycle, String> {
    List<Cycle> findByUserIdOrderByStartDateDesc(String userId);
    Optional<Cycle> findByIdAndUserId(String id, String userId);
    Optional<Cycle> findFirstByUserIdOrderByStartDateDesc(String userId);

}
