package com.hi.api.repository;

import com.hi.api.model.SymptomCategory;
import com.hi.api.model.SymptomDictionary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SymptomDictionaryRepository extends MongoRepository<SymptomDictionary, Long> {
    List<SymptomDictionary> findByActiveTrueOrderByCategoryAscNameAsc();
    List<SymptomDictionary> findByCategoryAndActiveTrueOrderByNameAsc(SymptomCategory category);
    Optional<SymptomDictionary> findByIdAndActiveTrue(Long id);
    Optional<SymptomDictionary> findByNameIgnoreCase(String name);
}
