package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@CompoundIndex(name = "chat_user_created_idx", def = "{'userId': 1, 'createdAt': 1}")
@Document(collection = "chats")
public class ChatMessage {
    public ChatMessage(User user, String request, String response) {
        this.request = request;
        this.user = user;
        this.response = response;
        if (user != null) {
            this.userId = user.getId();
        }
    }

    public ChatMessage(String userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
    }

    @Id
    @JsonProperty("_id")
    private String id;

    @JsonProperty("user")
    private User user;

    @Indexed
    private String userId;

    private String role;
    private String content;

    private String response;
    private String request;
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

