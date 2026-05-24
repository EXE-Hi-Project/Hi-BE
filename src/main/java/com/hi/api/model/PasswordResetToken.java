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

    // Lưu trữ mã OTP 6 số (có thể băm nếu muốn bảo mật tuyệt đối, ở đây lưu plain text cho dễ test)
    private String tokenHash;

    private Instant expiresAt;
    private Instant usedAt;

    private String requestedIp;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}