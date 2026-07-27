package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "chat_stream_requests")
@CompoundIndex(
        name = "user_idempotency_unique",
        def = "{'userId': 1, 'idempotencyKey': 1}",
        unique = true
)
public class ChatStreamRequest {

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }

    @Id
    private String id;

    private String userId;
    private String idempotencyKey;
    private LocalDate sessionDate;
    private Status status;
    private String userMessageId;
    private String assistantMessageId;
    private String failureMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
