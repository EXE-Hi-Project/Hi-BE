package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "user_devices")
@CompoundIndex(name = "user_device_unique", def = "{'userId': 1, 'deviceId': 1}", unique = true)
public class UserDevice {
    @Id
    private String id;
    private String userId;
    private String deviceId;
    private String platform;
    private String expoPushToken;
    private String appVersion;
    private Boolean active = true;
    private Instant lastSeenAt;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
