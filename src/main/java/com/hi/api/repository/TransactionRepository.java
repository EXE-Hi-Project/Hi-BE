package com.hi.api.repository;

import com.hi.api.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByOrderCode(Long orderCode);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Transaction> findTop50ByOrderByCreatedAtDesc();
    long countByStatusIgnoreCase(String status);
}
