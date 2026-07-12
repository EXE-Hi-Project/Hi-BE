package com.hi.api.repository;

import com.hi.api.model.OtpDelivery;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OtpDeliveryRepository extends MongoRepository<OtpDelivery, String> {
    Optional<OtpDelivery> findByDeliveryId(String deliveryId);
    Optional<OtpDelivery> findByProviderMessageId(String providerMessageId);
    List<OtpDelivery> findTop10ByUserIdOrderByAttemptedAtDesc(String userId);
    List<OtpDelivery> findByUserIdInOrderByAttemptedAtDesc(Collection<String> userIds);
}
