package com.hi.api.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {

    private static final int MAX_BUCKETS = 20_000;
    private static final String MESSAGE = "Quá nhiều yêu cầu xác thực. Vui lòng thử lại sau.";

    private final RateLimitService rateLimitService;
    private final Map<String, Bucket> fallbackBuckets = new ConcurrentHashMap<>();

    public AuthRateLimitService(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    AuthRateLimitService() {
        this.rateLimitService = null;
    }

    public void check(String action, String email, String clientIp, int maxAttempts, int windowMinutes) {
        String normalizedEmail = normalize(email);
        String normalizedIp = normalize(clientIp);
        if (rateLimitService != null) {
            Duration window = Duration.ofMinutes(windowMinutes);
            rateLimitService.check(action + ":email", normalizedEmail, maxAttempts, window, MESSAGE);
            rateLimitService.check(action + ":ip", normalizedIp, maxAttempts * 4, window, MESSAGE);
            return;
        }

        long windowSeconds = windowMinutes * 60L;
        long now = Instant.now().getEpochSecond();
        increment(action + ":email:" + normalizedEmail, now, windowSeconds, maxAttempts);
        increment(action + ":ip:" + normalizedIp, now, windowSeconds, maxAttempts * 4);

        if (fallbackBuckets.size() > MAX_BUCKETS) {
            fallbackBuckets.entrySet().removeIf(entry -> entry.getValue().windowStartedAt + windowSeconds < now);
        }
    }

    private void increment(String key, long now, long windowSeconds, int maxAttempts) {
        Bucket bucket = fallbackBuckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartedAt + windowSeconds <= now) {
                return new Bucket(now, 1);
            }
            existing.attempts++;
            return existing;
        });
        if (bucket.attempts > maxAttempts) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class Bucket {
        private final long windowStartedAt;
        private int attempts;

        private Bucket(long windowStartedAt, int attempts) {
            this.windowStartedAt = windowStartedAt;
            this.attempts = attempts;
        }
    }
}
