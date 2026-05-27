package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @JsonProperty("_id")
    private String id;

    private String actorUserId;
    private String targetUserId;
    private String action;
    private String entityType;
    private String entityId;
    private String beforeData;
    private String afterData;
    private String ipAddress;

    @CreatedDate
    private Instant createdAt;
}