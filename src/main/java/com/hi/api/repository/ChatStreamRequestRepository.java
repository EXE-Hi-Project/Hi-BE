package com.hi.api.repository;

import com.hi.api.model.ChatStreamRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChatStreamRequestRepository extends MongoRepository<ChatStreamRequest, String> {
    Optional<ChatStreamRequest> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
