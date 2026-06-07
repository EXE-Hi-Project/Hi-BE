package com.hi.api.repository;

import com.hi.api.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ChatRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<ChatMessage> findByUserIdAndSessionDateOrderByCreatedAtAsc(String userId, LocalDate sessionDate);
    List<ChatMessage> findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(String userId, Instant start, Instant end);
}
