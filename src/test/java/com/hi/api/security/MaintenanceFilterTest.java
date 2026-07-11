package com.hi.api.security;

import com.hi.api.service.SystemMaintenanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksUserApiDuringMaintenance() throws Exception {
        SystemMaintenanceService service = mock(SystemMaintenanceService.class);
        when(service.isActive()).thenReturn(true);
        MaintenanceFilter filter = new MaintenanceFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cycles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("MAINTENANCE_ACTIVE", response.getContentAsString().contains("MAINTENANCE_ACTIVE") ? "MAINTENANCE_ACTIVE" : "");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsAdminAndPublicStatusEndpoint() throws Exception {
        SystemMaintenanceService service = mock(SystemMaintenanceService.class);
        when(service.isActive()).thenReturn(true);
        MaintenanceFilter filter = new MaintenanceFilter(service);
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/admin/system-health"), new MockHttpServletResponse(), chain);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        SecurityContextHolder.clearContext();
        FilterChain publicChain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/system/maintenance"), new MockHttpServletResponse(), publicChain);
        verify(publicChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
