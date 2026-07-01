package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "analytics_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_analytics_event_created", def = "{'eventType': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_analytics_event_target_created", def = "{'eventType': 1, 'target': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_analytics_session_created", def = "{'sessionId': 1, 'createdAt': -1}")
})
public class AnalyticsEvent {

    @Id
    @JsonProperty("_id")
    private String id;

    @Indexed
    private String sessionId;

    @Indexed
    private String userId; // optional, null for guest

    @Indexed
    private String eventType; // PAGE_VIEW, CLICK, REGISTER, ONBOARDING_COMPLETE

    private String target; // route path for page view, element id / description for click

    private String elementText; // text of clicked element

    @Indexed
    @CreatedDate
    private Instant createdAt;

    private Map<String, Object> metadata; // extra metadata
}
