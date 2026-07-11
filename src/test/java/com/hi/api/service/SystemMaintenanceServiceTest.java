package com.hi.api.service;

import com.hi.api.dto.request.UpdateMaintenanceRequest;
import com.hi.api.model.MaintenanceMode;
import com.hi.api.model.SystemMaintenance;
import com.hi.api.repository.AdminAuditLogRepository;
import com.hi.api.repository.SystemMaintenanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMaintenanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-11T03:00:00Z");

    @Test
    void immediateMaintenanceIsActiveUntilItsEnd() {
        SystemMaintenanceRepository repository = mock(SystemMaintenanceRepository.class);
        AdminAuditLogRepository audits = mock(AdminAuditLogRepository.class);
        SystemMaintenanceService service = service(repository, audits);
        when(repository.findById(SystemMaintenance.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(SystemMaintenance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMaintenanceRequest request = request(MaintenanceMode.IMMEDIATE, null, NOW.plusSeconds(3600));
        SystemMaintenanceService.MaintenanceView result = service.update("admin-1", "127.0.0.1", request);

        assertTrue(result.active());
        assertEquals(NOW, result.startsAt());
        verify(audits).save(any());
    }

    @Test
    void scheduledMaintenanceIsInactiveBeforeStartAndActiveDuringWindow() {
        SystemMaintenanceRepository repository = mock(SystemMaintenanceRepository.class);
        SystemMaintenance config = new SystemMaintenance();
        config.setEnabled(true);
        config.setMode(MaintenanceMode.SCHEDULED);
        config.setStartsAt(NOW.plusSeconds(600));
        config.setEndsAt(NOW.plusSeconds(3600));
        when(repository.findById(SystemMaintenance.DEFAULT_ID)).thenReturn(Optional.of(config));

        assertFalse(service(repository, mock(AdminAuditLogRepository.class)).publicStatus().active());
        assertTrue(serviceAt(repository, mock(AdminAuditLogRepository.class), NOW.plusSeconds(900)).publicStatus().active());
    }

    @Test
    void scheduledMaintenanceRejectsInvalidWindow() {
        SystemMaintenanceRepository repository = mock(SystemMaintenanceRepository.class);
        when(repository.findById(SystemMaintenance.DEFAULT_ID)).thenReturn(Optional.empty());
        UpdateMaintenanceRequest request = request(MaintenanceMode.SCHEDULED, NOW.plusSeconds(3600), NOW.plusSeconds(1200));

        assertThrows(IllegalArgumentException.class,
                () -> service(repository, mock(AdminAuditLogRepository.class)).update("admin-1", "127.0.0.1", request));
    }

    private SystemMaintenanceService service(SystemMaintenanceRepository repository, AdminAuditLogRepository audits) {
        return serviceAt(repository, audits, NOW);
    }

    private SystemMaintenanceService serviceAt(SystemMaintenanceRepository repository, AdminAuditLogRepository audits, Instant now) {
        return new SystemMaintenanceService(repository, audits, Clock.fixed(now, ZoneOffset.UTC));
    }

    private UpdateMaintenanceRequest request(MaintenanceMode mode, Instant startsAt, Instant endsAt) {
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setEnabled(true);
        request.setMode(mode);
        request.setTitle("Hi đang được chăm sóc");
        request.setMessage("Chúng mình đang nâng cấp.");
        request.setStartsAt(startsAt);
        request.setEndsAt(endsAt);
        return request;
    }
}
