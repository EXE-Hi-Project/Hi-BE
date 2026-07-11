package com.hi.api.security;

import com.hi.api.service.SystemMaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class MaintenanceFilter extends OncePerRequestFilter {
    private static final String RESPONSE_BODY = "{\"success\":false,\"message\":\"Hi đang được bảo trì\",\"data\":{\"code\":\"MAINTENANCE_ACTIVE\"}}";

    private final SystemMaintenanceService service;

    public MaintenanceFilter(SystemMaintenanceService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isBypassed(request) || isAdmin() || !service.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(RESPONSE_BODY);
    }

    private boolean isBypassed(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        if ("/health".equals(path) || "/api/system/maintenance".equals(path)) return true;
        if (path.startsWith("/api/admin/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) return true;
        if ("/api/payments/webhook".equals(path)) return true;
        return path.equals("/api/auth/csrf")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/me")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout");
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
