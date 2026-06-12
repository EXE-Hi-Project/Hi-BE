package com.hi.api.repository;

import com.hi.api.model.DailyLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyLogRepository extends MongoRepository<DailyLog, Long> {
    List<DailyLog> findByUserIdOrderByLogDateDesc(String userId);
    Page<DailyLog> findByUserIdOrderByLogDateDesc(String userId, Pageable pageable);
    List<DailyLog> findByUserIdAndLogDateBetweenOrderByLogDateDesc(String userId, LocalDate from, LocalDate to);
    List<DailyLog> findByUserIdAndLogDateGreaterThanEqualOrderByLogDateDesc(String userId, LocalDate from);
    List<DailyLog> findByUserIdAndLogDateLessThanEqualOrderByLogDateDesc(String userId, LocalDate to);
    Optional<DailyLog> findByUserIdAndLogDate(String userId, LocalDate logDate);
    Optional<DailyLog> findFirstByUserIdAndMoodScoreIsNotNullOrderByLogDateDesc(String userId);
    Optional<DailyLog> findByIdAndUserId(Long id, String userId);
}
