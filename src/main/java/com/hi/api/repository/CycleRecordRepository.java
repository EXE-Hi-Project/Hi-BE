package com.hi.api.repository;

import com.hi.api.model.CycleRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CycleRecordRepository extends MongoRepository<CycleRecord, Long> {
    List<CycleRecord> findByUserIdOrderByStartDateDesc(String userId);
    List<CycleRecord> findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(String userId);
    List<CycleRecord> findByUserIdAndStartDateBetweenOrderByStartDateDesc(String userId, LocalDate from, LocalDate to);
    List<CycleRecord> findByUserIdAndStartDateGreaterThanEqualOrderByStartDateDesc(String userId, LocalDate from);
    List<CycleRecord> findByUserIdAndStartDateLessThanEqualOrderByStartDateDesc(String userId, LocalDate to);
    Optional<CycleRecord> findByIdAndUserId(Long id, String userId);
}