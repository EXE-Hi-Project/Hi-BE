package com.hi.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Signs short-lived CSRF tokens so Vercel rewrites do not depend on forwarding
 * the browser's Cookie header to the upstream Render service.
 */
public final class HeaderCsrfTokenRepository implements CsrfTokenRepository {

    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] signingKey;

    public HeaderCsrfTokenRepository(String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("CSRF signing secret must not be blank");
        }
        this.signingKey = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String payload = System.currentTimeMillis() + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME,
                encodedPayload + "." + encode(sign(encodedPayload)));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // The token is self-contained and signed, so there is no server-side state to save.
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        String requestToken = request.getHeader(HEADER_NAME);
        if (requestToken == null || requestToken.isBlank()) return null;

        String token = isValid(requestToken) ? requestToken : unmask(requestToken);
        return isValid(token) ? new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, token) : null;
    }

    private boolean isValid(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), decode(parts[1]))) return false;
        try {
            String payload = new String(decode(parts[0]), StandardCharsets.UTF_8);
            int separator = payload.indexOf(':');
            if (separator <= 0 || separator == payload.length() - 1) return false;
            long issuedAt = Long.parseLong(payload.substring(0, separator));
            long now = System.currentTimeMillis();
            return issuedAt <= now + Duration.ofMinutes(1).toMillis()
                    && issuedAt >= now - TOKEN_TTL.toMillis();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign CSRF token", exception);
        }
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return new byte[0];
        }
    }

    /**
     * Spring Security's default request handler masks the emitted token to
     * mitigate BREACH. The request carries that masked value, while this
     * stateless repository must load the original signed value first.
     */
    private String unmask(String value) {
        byte[] masked = decode(value);
        if (masked.length == 0 || masked.length % 2 != 0) return "";

        int tokenLength = masked.length / 2;
        byte[] token = new byte[tokenLength];
        for (int index = 0; index < tokenLength; index++) {
            token[index] = (byte) (masked[tokenLength + index] ^ masked[index]);
        }
        return new String(token, StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
