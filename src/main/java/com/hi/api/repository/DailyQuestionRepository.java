package com.hi.api.repository;

import com.hi.api.model.DailyQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DailyQuestionRepository extends MongoRepository<DailyQuestion, String> {
    List<DailyQuestion> findByActiveTrueOrderByDisplayOrderAsc();
    long countByActiveTrue();
    Optional<DailyQuestion> findFirstByOrderByDisplayOrderDesc();
}
