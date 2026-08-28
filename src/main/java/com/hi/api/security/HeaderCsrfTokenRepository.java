package com.hi.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps CSRF tokens server-side so Vercel rewrites do not depend on forwarding
 * the browser's Cookie header to the upstream Render service.
 */
public final class HeaderCsrfTokenRepository implements CsrfTokenRepository {

    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_ACTIVE_TOKENS = 10_000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> tokenExpirations = new ConcurrentHashMap<>();

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        cleanupExpiredTokens();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME,
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null) return;
        cleanupExpiredTokens();
        if (tokenExpirations.size() >= MAX_ACTIVE_TOKENS) {
            tokenExpirations.clear();
        }
        tokenExpirations.put(token.getToken(), Instant.now().plus(TOKEN_TTL));
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        String token = request.getHeader(HEADER_NAME);
        if (token == null || token.isBlank()) return null;

        Instant expiresAt = tokenExpirations.get(token);
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            tokenExpirations.remove(token);
            return null;
        }
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, token);
    }

    private void cleanupExpiredTokens() {
        Instant now = Instant.now();
        tokenExpirations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
