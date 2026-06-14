package com.hi.api.service;

import com.google.common.hash.Hashing;
import com.hi.api.dto.request.VerifyOtpRequest;
import com.hi.api.model.PasswordResetToken;
import com.hi.api.model.User;
import com.hi.api.repository.PasswordResetTokenRepository;
import com.hi.api.repository.UserRepository;
import com.hi.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceOtpTest {

    @Test
    void locksOtpAfterFiveFailedAttempts() {
        PasswordResetToken token = activeToken("654321");
        AuthService service = serviceFor(token);
        VerifyOtpRequest request = request("user@example.com", "000000");

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(request));
        }

        assertTrue(token.getFailedAttempts() >= 5);
        assertNotNull(token.getLockedUntil());
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(request));
    }

    @Test
    void validOtpCreatesOneTimeResetToken() {
        PasswordResetToken token = activeToken("654321");
        AuthService service = serviceFor(token);

        String resetToken = service.verifyOtp(request("user@example.com", "654321"));

        assertNotNull(resetToken);
        assertTrue(Boolean.TRUE.equals(token.getOtpVerified()));
        assertNotNull(token.getTokenHash());
    }

    private AuthService serviceFor(PasswordResetToken token) {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        when(tokenRepository.findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(token));

        return new AuthService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(JwtUtil.class),
                tokenRepository,
                mock(RestTemplate.class),
                mock(EmailService.class),
                mock(RealtimeEventService.class)
        );
    }

    private PasswordResetToken activeToken(String otp) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId("user-1");
        token.setOtpHash(Hashing.sha256().hashString(otp, StandardCharsets.UTF_8).toString());
        token.setOtpVerified(false);
        token.setFailedAttempts(0);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        return token;
    }

    private VerifyOtpRequest request(String email, String otp) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return request;
    }
}
