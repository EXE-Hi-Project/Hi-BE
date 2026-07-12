package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data @NoArgsConstructor
@Document(collection = "support_ticket_messages")
public class SupportMessage {
    public enum Actor { USER, ADMIN }
    @Id @JsonProperty("_id") private String id;
    @Indexed private String ticketId;
    private String authorId;
    private Actor actor;
    private String content;
    @CreatedDate private Instant createdAt;
}
