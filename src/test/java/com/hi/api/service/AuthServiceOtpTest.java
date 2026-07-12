package com.hi.api.service;

import com.google.common.hash.Hashing;
import com.hi.api.dto.request.VerifyOtpRequest;
import com.hi.api.dto.request.RegisterRequest;
import com.hi.api.exception.OtpDeliveryException;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceOtpTest {

    @Test
    void registerExistingPendingAccountReturnsActivationFlowWithoutCreatingDuplicate() {
        User pending = user("user-1", "user@example.com", "PENDING_ACTIVATION");
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        EmailService emails = mock(EmailService.class);
        AuthService service = serviceFor(users, tokens, emails);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("USER@example.com");
        request.setName("User");
        request.setPassword("password123");
        request.setGender("female");

        Map<String, Object> result = service.register(request);

        assertEquals(true, result.get("pendingActivation"));
        assertEquals(true, result.get("existingPendingAccount"));
        verify(users, never()).save(any(User.class));
        verify(emails, never()).sendRegistrationOtpEmail(any(), any());
    }

    @Test
    void failedResendInvalidatesOnlyNewTokenAndKeepsPreviousOtpUsable() {
        User pending = user("user-1", "user@example.com", "PENDING_ACTIVATION");
        PasswordResetToken previous = activeToken("111111");
        previous.setCreatedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        when(tokens.findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(previous));
        when(tokens.findByUserIdAndUsedAtIsNull("user-1")).thenReturn(List.of(previous));
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EmailService emails = mock(EmailService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp unavailable"))
                .when(emails).sendRegistrationOtpEmail(any(), any());
        AuthService service = serviceFor(users, tokens, emails);

        assertThrows(OtpDeliveryException.class, () -> service.resendActivationOtp("user@example.com"));

        assertNull(previous.getUsedAt());
        verify(tokens, org.mockito.Mockito.atLeast(2)).save(any(PasswordResetToken.class));
    }

    @Test
    void verifiedGoogleEmailActivatesAndLinksPendingLocalAccount() {
        User pending = user("user-1", "user@example.com", "PENDING_ACTIVATION");
        pending.setAuthProvider("local");
        PasswordResetToken openToken = activeToken("111111");
        UserRepository users = mock(UserRepository.class);
        when(users.findByGoogleId("google-1")).thenReturn(Optional.empty());
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        when(tokens.findByUserIdAndUsedAtIsNull("user-1")).thenReturn(List.of(openToken));
        AuthService service = serviceFor(users, tokens, mock(EmailService.class));

        service.authenticateGoogleUser("google-1", "user@example.com", "User", null, true);

        assertEquals("ACTIVE", pending.getAccountStatus());
        assertEquals("google-1", pending.getGoogleId());
        assertEquals("local", pending.getAuthProvider());
        assertNotNull(openToken.getUsedAt());
        verify(users).save(pending);
    }

    @Test
    void unverifiedGoogleEmailCannotLinkAccount() {
        AuthService service = serviceFor(mock(UserRepository.class), mock(PasswordResetTokenRepository.class),
                mock(EmailService.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.authenticateGoogleUser("google-1", "user@example.com", "User", null, false));
    }

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
        User user = user("user-1", "user@example.com", "ACTIVE");

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        when(tokenRepository.findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(token));

        return serviceFor(userRepository, tokenRepository, mock(EmailService.class));
    }

    @Test
    void forgotPasswordRejectsUnknownEmailWithoutCreatingOtp() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        EmailService emails = mock(EmailService.class);
        AuthService service = serviceFor(users, tokens, emails);
        com.hi.api.dto.request.ForgotPasswordRequest request = new com.hi.api.dto.request.ForgotPasswordRequest();
        request.setEmail("missing@example.com");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.forgotPassword(request));

        assertEquals("Email không tồn tại trong hệ thống.", error.getMessage());
        verify(tokens, never()).save(any(PasswordResetToken.class));
        verify(emails, never()).sendOtpEmail(any(), any());
    }

    @Test
    void googleAccountCanRequestOtpToCreatePassword() {
        User googleUser = user("user-1", "user@example.com", "ACTIVE");
        googleUser.setAuthProvider("google");
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(googleUser));
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        when(tokens.findByUserIdAndUsedAtIsNull("user-1")).thenReturn(List.of());
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EmailService emails = mock(EmailService.class);
        AuthService service = serviceFor(users, tokens, emails);
        com.hi.api.dto.request.ForgotPasswordRequest request = new com.hi.api.dto.request.ForgotPasswordRequest();
        request.setEmail("user@example.com");

        service.forgotPassword(request);

        verify(emails).sendOtpEmail(eq(googleUser), any());
    }

    @Test
    void facebookAccountStillCannotRequestPasswordOtp() {
        User facebookUser = user("user-1", "user@example.com", "ACTIVE");
        facebookUser.setAuthProvider("facebook");
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(facebookUser));
        AuthService service = serviceFor(users, mock(PasswordResetTokenRepository.class), mock(EmailService.class));
        com.hi.api.dto.request.ForgotPasswordRequest request = new com.hi.api.dto.request.ForgotPasswordRequest();
        request.setEmail("user@example.com");

        assertThrows(IllegalArgumentException.class, () -> service.forgotPassword(request));
    }

    @Test
    void otpDeliveryExceptionDoesNotEmbedTrackingIdInPublicMessage() {
        OtpDeliveryException exception = new OtpDeliveryException("D97D1004");
        assertFalse(exception.getMessage().contains("D97D1004"));
    }

    private AuthService serviceFor(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                   EmailService emailService) {
        return new AuthService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(JwtUtil.class),
                tokenRepository,
                mock(RestTemplate.class),
                emailService,
                mock(RealtimeEventService.class)
        );
    }

    private User user(String id, String email, String status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName("User");
        user.setAccountStatus(status);
        return user;
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
