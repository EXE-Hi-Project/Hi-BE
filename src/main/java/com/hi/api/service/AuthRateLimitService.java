package com.hi.api.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {

    private static final int MAX_BUCKETS = 20_000;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String action, String email, String clientIp, int maxAttempts, int windowMinutes) {
        long windowSeconds = windowMinutes * 60L;
        long now = Instant.now().getEpochSecond();
        String normalizedEmail = email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();

        increment(action + ":email:" + normalizedEmail, now, windowSeconds, maxAttempts);
        increment(action + ":ip:" + normalizedIp, now, windowSeconds, maxAttempts * 4);

        if (buckets.size() > MAX_BUCKETS) {
            buckets.entrySet().removeIf(entry -> entry.getValue().windowStartedAt + windowSeconds < now);
        }
    }

    private void increment(String key, long now, long windowSeconds, int maxAttempts) {
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartedAt + windowSeconds <= now) {
                return new Bucket(now, 1);
            }
            existing.attempts++;
            return existing;
        });
        if (bucket.attempts > maxAttempts) {
            throw new IllegalArgumentException("Quá nhiều yêu cầu xác thực. Vui lòng thử lại sau.");
        }
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
