package com.hi.api.config;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import com.hi.api.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "socketPrincipal";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public WebSocketAuthHandshakeInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = readAccessToken(servletRequest.getServletRequest());
        if (token == null || !jwtUtil.validateToken(token)) {
            return false;
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        String accountStatus = user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE";
        if ("LOCKED".equalsIgnoreCase(accountStatus) || "DELETED".equalsIgnoreCase(accountStatus)) {
            return false;
        }

        attributes.put(PRINCIPAL_ATTRIBUTE, new SocketPrincipal(userId));
        attributes.put("role", user.getRole() != null ? user.getRole() : "user");
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private String readAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("hi_access_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
