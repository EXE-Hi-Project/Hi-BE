package com.hi.api.repository;

import com.hi.api.model.CycleRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CycleRecordRepository extends MongoRepository<CycleRecord, Long> {
    List<CycleRecord> findByUserIdOrderByStartDateDesc(String userId);
    Page<CycleRecord> findByUserIdOrderByStartDateDesc(String userId, Pageable pageable);
    List<CycleRecord> findByUserIdAndIsIgnoredFalseOrderByStartDateDesc(String userId);
    List<CycleRecord> findByUserIdAndStartDateBetweenOrderByStartDateDesc(String userId, LocalDate from, LocalDate to);
    List<CycleRecord> findByUserIdAndStartDateGreaterThanEqualOrderByStartDateDesc(String userId, LocalDate from);
    List<CycleRecord> findByUserIdAndStartDateLessThanEqualOrderByStartDateDesc(String userId, LocalDate to);
    Optional<CycleRecord> findByIdAndUserId(Long id, String userId);
    Optional<CycleRecord> findByUserIdAndStartDate(String userId, LocalDate startDate);
}
