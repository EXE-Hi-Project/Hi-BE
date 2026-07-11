package com.hi.api.service;

import com.hi.api.dto.request.UpdateMaintenanceRequest;
import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.MaintenanceMode;
import com.hi.api.model.SystemMaintenance;
import com.hi.api.repository.AdminAuditLogRepository;
import com.hi.api.repository.SystemMaintenanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class SystemMaintenanceService {
    private static final long CACHE_TTL_MILLIS = 5_000;
    private final SystemMaintenanceRepository repository;
    private final AdminAuditLogRepository auditLogRepository;
    private final Clock clock;
    private volatile CachedConfig cachedConfig;

    @Autowired
    public SystemMaintenanceService(SystemMaintenanceRepository repository,
                                    AdminAuditLogRepository auditLogRepository) {
        this(repository, auditLogRepository, Clock.systemUTC());
    }

    SystemMaintenanceService(SystemMaintenanceRepository repository,
                             AdminAuditLogRepository auditLogRepository,
                             Clock clock) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public MaintenanceView publicStatus() {
        return view(current(), Instant.now(clock));
    }

    public MaintenanceView adminStatus() {
        return view(current(), Instant.now(clock));
    }

    public boolean isActive() {
        return isActive(current(), Instant.now(clock));
    }

    public MaintenanceView update(String adminId, String ipAddress, UpdateMaintenanceRequest request) {
        Instant now = Instant.now(clock);
        SystemMaintenance config = current();
        String before = summarize(config);

        Instant startsAt = request.getStartsAt();
        Instant endsAt = request.getEndsAt();
        if (request.isEnabled()) {
            if (endsAt == null || !endsAt.isAfter(now)) {
                throw new IllegalArgumentException("Thời gian kết thúc phải ở tương lai");
            }
            if (request.getMode() == MaintenanceMode.IMMEDIATE) {
                startsAt = now;
            } else if (startsAt == null || !startsAt.isAfter(now)) {
                throw new IllegalArgumentException("Thời gian bắt đầu theo lịch phải ở tương lai");
            }
            if (!startsAt.isBefore(endsAt)) {
                throw new IllegalArgumentException("Thời gian bắt đầu phải trước thời gian kết thúc");
            }
        }

        config.setEnabled(request.isEnabled());
        config.setMode(request.getMode());
        config.setTitle(request.getTitle().trim());
        config.setMessage(request.getMessage().trim());
        config.setStartsAt(startsAt);
        config.setEndsAt(endsAt);
        config.setUpdatedBy(adminId);
        SystemMaintenance saved = repository.save(config);
        cache(saved);
        audit(adminId, request.isEnabled() ? "UPDATE_MAINTENANCE" : "DISABLE_MAINTENANCE",
                before, summarize(saved), ipAddress);
        return view(saved, now);
    }

    public MaintenanceView disable(String adminId, String ipAddress) {
        SystemMaintenance config = current();
        String before = summarize(config);
        config.setEnabled(false);
        config.setUpdatedBy(adminId);
        SystemMaintenance saved = repository.save(config);
        cache(saved);
        audit(adminId, "DISABLE_MAINTENANCE", before, summarize(saved), ipAddress);
        return view(saved, Instant.now(clock));
    }

    private SystemMaintenance current() {
        long now = System.currentTimeMillis();
        CachedConfig cached = cachedConfig;
        if (cached != null && now - cached.loadedAtMillis < CACHE_TTL_MILLIS) {
            return cached.config;
        }
        SystemMaintenance loaded = repository.findById(SystemMaintenance.DEFAULT_ID).orElseGet(SystemMaintenance::new);
        cachedConfig = new CachedConfig(loaded, now);
        return loaded;
    }

    private void cache(SystemMaintenance config) {
        cachedConfig = new CachedConfig(config, System.currentTimeMillis());
    }

    private boolean isActive(SystemMaintenance config, Instant now) {
        if (!config.isEnabled() || config.getEndsAt() == null || !now.isBefore(config.getEndsAt())) {
            return false;
        }
        return config.getStartsAt() == null || !now.isBefore(config.getStartsAt());
    }

    private MaintenanceView view(SystemMaintenance config, Instant now) {
        return new MaintenanceView(
                config.isEnabled(),
                isActive(config, now),
                config.isEnabled() && config.getStartsAt() != null && now.isBefore(config.getStartsAt()),
                config.getMode(),
                config.getTitle(),
                config.getMessage(),
                config.getStartsAt(),
                config.getEndsAt(),
                config.getUpdatedBy(),
                config.getUpdatedAt()
        );
    }

    private void audit(String actorUserId, String action, String before, String after, String ipAddress) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType("SYSTEM_MAINTENANCE");
        log.setEntityId(SystemMaintenance.DEFAULT_ID);
        log.setBeforeData(before);
        log.setAfterData(after);
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);
    }

    private String summarize(SystemMaintenance config) {
        return "enabled=" + config.isEnabled()
                + ",mode=" + config.getMode()
                + ",startsAt=" + config.getStartsAt()
                + ",endsAt=" + config.getEndsAt();
    }

    public record MaintenanceView(
            boolean enabled,
            boolean active,
            boolean scheduled,
            MaintenanceMode mode,
            String title,
            String message,
            Instant startsAt,
            Instant endsAt,
            String updatedBy,
            Instant updatedAt
    ) {}

    private record CachedConfig(SystemMaintenance config, long loadedAtMillis) {}
}
