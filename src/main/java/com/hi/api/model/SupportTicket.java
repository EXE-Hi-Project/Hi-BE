package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data @NoArgsConstructor
@Document(collection = "support_tickets")
public class SupportTicket {
    public enum Category { ACCOUNT, PAYMENT, TECHNICAL, DATA_PRIVACY, OTHER }
    public enum Status { OPEN, IN_PROGRESS, WAITING_FOR_USER, CLOSED }

    @Id @JsonProperty("_id") private String id;
    @Indexed(unique = true) private String ticketCode;
    @Indexed private String userId;
    private String userName;
    private String userEmail;
    private String title;
    @Indexed private Category category;
    @Indexed private Status status;
    private String assignedAdminId;
    @Indexed private Instant lastMessageAt;
    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
}
