package com.hi.api.repository;

import com.hi.api.model.VoucherOrder;
import com.hi.api.model.VoucherOrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface VoucherOrderRepository extends MongoRepository<VoucherOrder, Long> {
    Optional<VoucherOrder> findByOrderCode(Long orderCode);
    List<VoucherOrder> findByUserIdOrderByCreatedAtDesc(String userId);
    List<VoucherOrder> findByStatusOrderByCreatedAtAsc(VoucherOrderStatus status);
    boolean existsByOrderCode(Long orderCode);
}
