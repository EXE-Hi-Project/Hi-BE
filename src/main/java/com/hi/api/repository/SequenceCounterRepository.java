package com.hi.api.repository;

import com.hi.api.model.SequenceCounter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SequenceCounterRepository extends MongoRepository<SequenceCounter, String> {
}