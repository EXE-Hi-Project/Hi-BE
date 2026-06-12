package com.hi.api.repository;

import com.hi.api.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndOtpVerifiedTrue(String tokenHash);
    java.util.List<PasswordResetToken> findByUserIdAndUsedAtIsNull(String userId);
    Optional<PasswordResetToken> findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc(String userId);
}
