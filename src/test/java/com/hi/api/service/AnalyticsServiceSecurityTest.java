package com.hi.api.service;

import com.hi.api.dto.request.TrackEventRequest;
import com.hi.api.model.AnalyticsEvent;
import com.hi.api.model.User;
import com.hi.api.repository.AnalyticsEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceSecurityTest {

    @Test
    void ignoresClientSuppliedUserIdAndBoundsMetadata() {
        AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
        when(repository.save(any(AnalyticsEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnalyticsService service = new AnalyticsService(repository, mock(MongoTemplate.class));

        TrackEventRequest request = new TrackEventRequest();
        request.setSessionId("session-1");
        request.setUserId("spoofed-user");
        request.setEventType("CLICK");
        request.setTarget("/target");
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            metadata.put("key-" + i, "x".repeat(500));
        }
        request.setMetadata(metadata);

        User principal = new User();
        principal.setId("real-user");
        AnalyticsEvent event = service.trackEvent(request, principal, "127.0.0.1");

        assertEquals("real-user", event.getUserId());
        assertEquals(12, event.getMetadata().size());
        assertFalse(event.getMetadata().values().stream().anyMatch(value -> String.valueOf(value).length() > 200));
    }
}
