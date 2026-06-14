package com.hi.api.config;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import com.hi.api.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthHandshakeInterceptorTest {

    @Test
    void validAccessCookieCreatesUserPrincipal() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketAuthHandshakeInterceptor interceptor =
                new WebSocketAuthHandshakeInterceptor(jwtUtil, userRepository);
        User user = new User();
        user.setId("user-1");
        user.setRole("admin");
        user.setAccountStatus("ACTIVE");

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("valid-token")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("hi_access_token", "valid-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                mock(org.springframework.http.server.ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertTrue(accepted);
        Principal principal = assertInstanceOf(
                Principal.class,
                attributes.get(WebSocketAuthHandshakeInterceptor.PRINCIPAL_ATTRIBUTE)
        );
        assertEquals("user-1", principal.getName());
        assertEquals("admin", attributes.get("role"));
    }

    @Test
    void missingAccessCookieRejectsHandshake() {
        WebSocketAuthHandshakeInterceptor interceptor = new WebSocketAuthHandshakeInterceptor(
                mock(JwtUtil.class),
                mock(UserRepository.class)
        );

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                mock(org.springframework.http.server.ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(accepted);
    }
}
