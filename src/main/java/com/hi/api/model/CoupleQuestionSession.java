package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "couple_question_sessions")
@CompoundIndexes({
        @CompoundIndex(name = "couple_question_pair_date_idx", def = "{ 'pairKey': 1, 'questionDate': 1 }", unique = true),
        @CompoundIndex(name = "couple_question_participant_date_idx", def = "{ 'participantIds': 1, 'questionDate': -1 }")
})
public class CoupleQuestionSession {

    @Id
    @JsonProperty("_id")
    private String id;

    private String pairKey;
    private LocalDate questionDate;
    private String questionId;
    private String questionText;
    private String category;
    private List<String> participantIds = new ArrayList<>();
    private Map<String, Answer> answers = new LinkedHashMap<>();
    private List<String> skippedBy = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private Instant unlockedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    public static class Answer {
        private String userId;
        private String content;
        private Instant answeredAt;
        private Instant updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class Message {
        private String id;
        private String userId;
        private String content;
        private Instant createdAt;
    }
}
