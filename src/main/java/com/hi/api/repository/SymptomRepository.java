package com.hi.api.repository;

import com.hi.api.model.Symptom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SymptomRepository extends MongoRepository<Symptom, String> {
    List<Symptom> findByUserIdOrderByDateDesc(String userId);
    Optional<Symptom> findByIdAndUserId(String id, String userId);
}
