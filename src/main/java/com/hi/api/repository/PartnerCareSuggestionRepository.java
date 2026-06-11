package com.hi.api.repository;

import com.hi.api.model.PartnerCareSuggestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PartnerCareSuggestionRepository extends MongoRepository<PartnerCareSuggestion, String> {
    Optional<PartnerCareSuggestion> findByRecipientUserIdAndSuggestionDate(String recipientUserId, LocalDate suggestionDate);
}
