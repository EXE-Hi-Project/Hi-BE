package com.hi.api.service;

import com.hi.api.model.RateLimitBucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class RateLimitService {

    private final MongoTemplate mongoTemplate;
    private final String hashSecret;

    public RateLimitService(MongoTemplate mongoTemplate,
                            @Value("${app.rate-limit.hash-secret:${app.jwt.secret:dev-rate-limit-secret}}") String hashSecret) {
        this.mongoTemplate = mongoTemplate;
        this.hashSecret = hashSecret == null || hashSecret.isBlank() ? "dev-rate-limit-secret" : hashSecret;
    }

    public boolean tryConsume(String scope, String subject, int maxAttempts, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        long nowEpoch = Instant.now().getEpochSecond();
        long windowStart = nowEpoch / windowSeconds;
        String id = normalize(scope) + ":" + hmac(normalize(subject)) + ":" + windowStart;
        Instant expiresAt = Instant.ofEpochSecond((windowStart + 1) * windowSeconds).plusSeconds(60);

        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .inc("count", 1)
                .setOnInsert("expiresAt", expiresAt);
        RateLimitBucket bucket = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                RateLimitBucket.class
        );
        return bucket == null || bucket.getCount() <= maxAttempts;
    }

    public void check(String scope, String subject, int maxAttempts, Duration window, String message) {
        if (!tryConsume(scope, subject, maxAttempts, window)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash rate-limit key", e);
        }
    }
}
