package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "system_maintenance")
public class SystemMaintenance {
    public static final String DEFAULT_ID = "default";
    public static final String DEFAULT_TITLE = "Hi đang được chăm sóc";
    public static final String DEFAULT_MESSAGE = "Chúng mình đang nâng cấp để trải nghiệm của bạn tốt hơn.";

    @Id
    private String id = DEFAULT_ID;
    private boolean enabled;
    private MaintenanceMode mode = MaintenanceMode.IMMEDIATE;
    private String title = DEFAULT_TITLE;
    private String message = DEFAULT_MESSAGE;
    private Instant startsAt;
    private Instant endsAt;
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;
}
