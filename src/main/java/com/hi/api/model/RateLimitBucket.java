package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "rate_limit_buckets")
public class RateLimitBucket {

    @Id
    private String id;

    private long count;

    @Indexed(name = "rate_limit_bucket_expires_ttl", expireAfterSeconds = 0)
    private Instant expiresAt;
}
