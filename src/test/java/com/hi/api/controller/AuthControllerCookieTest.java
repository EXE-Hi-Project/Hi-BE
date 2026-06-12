package com.hi.api.controller;

import com.hi.api.dto.request.LoginRequest;
import com.hi.api.model.User;
import com.hi.api.service.AuthRateLimitService;
import com.hi.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerCookieTest {

    @Test
    void loginSetsHttpOnlySameSiteCookie() {
        AuthService authService = mock(AuthService.class);
        User user = new User();
        user.setId("user-1");
        when(authService.login(org.mockito.ArgumentMatchers.any(LoginRequest.class)))
                .thenReturn(Map.of("token", "signed-jwt", "user", user));

        AuthController controller = new AuthController(authService, mock(AuthRateLimitService.class));
        ReflectionTestUtils.setField(controller, "secureAuthCookie", false);
        ReflectionTestUtils.setField(controller, "jwtExpirationMs", 60_000L);

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        String cookie = controller.login(request).getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(cookie);
        assertTrue(cookie.contains("hi_access_token=signed-jwt"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
    }
}
