package com.hi.api.repository;

import com.hi.api.model.CoupleQuestionSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CoupleQuestionSessionRepository extends MongoRepository<CoupleQuestionSession, String> {
    Optional<CoupleQuestionSession> findByPairKeyAndQuestionDate(String pairKey, LocalDate questionDate);
    Optional<CoupleQuestionSession> findByIdAndParticipantIdsContaining(String id, String userId);
    Page<CoupleQuestionSession> findByParticipantIdsContainingOrderByQuestionDateDesc(String userId, Pageable pageable);
    List<CoupleQuestionSession> findByPairKeyAndQuestionDateBetweenOrderByQuestionDateAsc(
            String pairKey, LocalDate from, LocalDate to);
    Optional<CoupleQuestionSession> findFirstByPairKeyAndUnlockedAtIsNotNullOrderByQuestionDateDesc(String pairKey);
    Optional<CoupleQuestionSession> findFirstByPairKeyOrderByQuestionDateDesc(String pairKey);
}
