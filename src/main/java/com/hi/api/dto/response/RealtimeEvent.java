package com.hi.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(
        String eventId,
        String type,
        Instant occurredAt,
        Object data
) {
    public static RealtimeEvent of(String type, Object data) {
        return new RealtimeEvent(UUID.randomUUID().toString(), type, Instant.now(), data);
    }
}
