package com.hi.api.repository;

import com.hi.api.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByUserIdAndTokenHashAndUsedAtIsNull(String userId, String tokenHash);
}