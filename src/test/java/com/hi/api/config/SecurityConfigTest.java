package com.hi.api.config;

import com.hi.api.repository.UserRepository;
import com.hi.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void corsDoesNotAllowWildcardHeadersWithCredentials() {
        SecurityConfig config = new SecurityConfig(mock(JwtUtil.class), mock(UserRepository.class));
        ReflectionTestUtils.setField(config, "allowedOriginsStr", "https://hilover.space");
        ReflectionTestUtils.setField(config, "allowVercelPreview", false);

        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/csrf"));

        assertEquals(List.of(
                "Content-Type",
                "Authorization",
                "X-XSRF-TOKEN",
                "X-Requested-With",
                "X-Client-Platform",
                "X-Idempotency-Key"
        ), cors.getAllowedHeaders());
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
    }

    @Test
    void nativeMobileApiRequestsBypassCookieCsrfBoundary() {
        MockHttpServletRequest android = new MockHttpServletRequest("POST", "/api/auth/login");
        android.addHeader("X-Client-Platform", "android");
        assertTrue(SecurityConfig.nativeMobileRequest().matches(android));

        MockHttpServletRequest web = new MockHttpServletRequest("POST", "/api/auth/login");
        assertFalse(SecurityConfig.nativeMobileRequest().matches(web));

        MockHttpServletRequest nonApi = new MockHttpServletRequest("POST", "/admin/action");
        nonApi.addHeader("X-Client-Platform", "android");
        assertFalse(SecurityConfig.nativeMobileRequest().matches(nonApi));
    }
}
