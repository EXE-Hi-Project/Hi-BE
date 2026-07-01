package com.hi.api.controller;

import com.hi.api.dto.request.LoginRequest;
import com.hi.api.dto.request.RegisterRequest;
import com.hi.api.model.User;
import com.hi.api.service.AuthRateLimitService;
import com.hi.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

        String cookie = controller.login(request, httpRequest()).getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        assertNotNull(cookie);
        assertTrue(cookie.contains("hi_access_token=signed-jwt"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
    }

    @Test
    void loginAndRegisterUseRateLimit() {
        AuthService authService = mock(AuthService.class);
        AuthRateLimitService rateLimitService = mock(AuthRateLimitService.class);
        User user = new User();
        user.setId("user-1");
        when(authService.login(org.mockito.ArgumentMatchers.any(LoginRequest.class)))
                .thenReturn(Map.of("token", "signed-jwt", "user", user));
        when(authService.register(org.mockito.ArgumentMatchers.any(RegisterRequest.class)))
                .thenReturn(Map.of("user", user));

        AuthController controller = new AuthController(authService, rateLimitService);
        ReflectionTestUtils.setField(controller, "secureAuthCookie", false);
        ReflectionTestUtils.setField(controller, "jwtExpirationMs", 60_000L);

        LoginRequest login = new LoginRequest();
        login.setEmail("user@example.com");
        login.setPassword("password123");
        controller.login(login, httpRequest());

        RegisterRequest register = new RegisterRequest();
        register.setName("Test User");
        register.setEmail("new@example.com");
        register.setPassword("password123");
        register.setGender("female");
        controller.register(register, httpRequest());

        verify(rateLimitService).check("login", "user@example.com", "127.0.0.1", 5, 15);
        verify(rateLimitService).check("register", "new@example.com", "127.0.0.1", 5, 15);
    }

    @Test
    void csrfEndpointReturnsHeaderNameAndToken() {
        AuthController controller = new AuthController(mock(AuthService.class), mock(AuthRateLimitService.class));
        CsrfToken token = mock(CsrfToken.class);
        when(token.getToken()).thenReturn("csrf-value");
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");

        ResponseEntity<Map<String, Object>> response = controller.csrf(token);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

        assertEquals("csrf-value", data.get("csrfToken"));
        assertEquals("X-XSRF-TOKEN", data.get("headerName"));
    }

    private HttpServletRequest httpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }
}
