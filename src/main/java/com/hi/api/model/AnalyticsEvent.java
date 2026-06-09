package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "analytics_events")
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
