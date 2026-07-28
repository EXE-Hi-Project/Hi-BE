package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "notifications")
@CompoundIndex(name = "notification_user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
public class Notification {

    @Id
    @JsonProperty("_id")
    private String id;

    private String userId;
    private String type;
    private String title;
    private String message;
    private String actionUrl;
    private String dedupeKey;
    private Map<String, Object> metadata;
    private Boolean read = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
