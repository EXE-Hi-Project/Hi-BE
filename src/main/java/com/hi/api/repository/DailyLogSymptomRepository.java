package com.hi.api.repository;

import com.hi.api.model.DailyLogSymptom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DailyLogSymptomRepository extends MongoRepository<DailyLogSymptom, Long> {
    List<DailyLogSymptom> findByDailyLogId(Long dailyLogId);
    List<DailyLogSymptom> findByDailyLogIdIn(List<Long> dailyLogIds);
    Optional<DailyLogSymptom> findByDailyLogIdAndSymptomId(Long dailyLogId, Long symptomId);
    void deleteByDailyLogId(Long dailyLogId);
    void deleteByDailyLogIdAndSymptomId(Long dailyLogId, Long symptomId);
}
