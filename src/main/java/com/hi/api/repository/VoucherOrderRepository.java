package com.hi.api.repository;

import com.hi.api.model.VoucherOrder;
import com.hi.api.model.VoucherOrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoucherOrderRepository extends MongoRepository<VoucherOrder, Long> {
    Optional<VoucherOrder> findByOrderCode(Long orderCode);
    List<VoucherOrder> findByUserIdOrderByCreatedAtDesc(String userId);
    List<VoucherOrder> findByStatusOrderByCreatedAtAsc(VoucherOrderStatus status);
    List<VoucherOrder> findByStatusAndIssuingStartedAtBefore(
            VoucherOrderStatus status,
            Instant issuingStartedAt);

    @Query("{ '_id': ?0, 'status': { '$in': ?1 } }")
    @Update("{ '$set': { 'status': 'ISSUING', 'paidAt': ?2, 'issuingStartedAt': ?2 } }")
    long claimForIssuance(Long id, List<VoucherOrderStatus> statuses, Instant claimedAt);

    boolean existsByOrderCode(Long orderCode);
}
