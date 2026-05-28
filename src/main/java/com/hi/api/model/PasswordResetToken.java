package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @JsonProperty("_id")
    private String id;

    private String userId;

    // Hash của OTP 6 số (phase 1 - forgot password)
    private String otpHash;

    // true sau khi user xác nhận OTP đúng
    private Boolean otpVerified = false;

    // Hash của UUID reset token (phase 2 - sau khi OTP xác minh thành công)
    private String tokenHash;

    private Instant expiresAt;
    private Instant usedAt;

    private String requestedIp;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}