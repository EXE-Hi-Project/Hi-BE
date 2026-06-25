package com.hi.api.repository;

import com.hi.api.model.CoupleAnniversary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CoupleAnniversaryRepository extends MongoRepository<CoupleAnniversary, String> {
    Optional<CoupleAnniversary> findByPairKeyAndType(String pairKey, CoupleAnniversary.Type type);
    List<CoupleAnniversary> findByPairKeyOrderByEventDateAsc(String pairKey);
    List<CoupleAnniversary> findByPairKeyAndTypeOrderByEventDateAsc(String pairKey, CoupleAnniversary.Type type);
    Optional<CoupleAnniversary> findByIdAndPairKeyAndType(String id, String pairKey, CoupleAnniversary.Type type);
}
