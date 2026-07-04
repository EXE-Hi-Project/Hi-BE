package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "couple_place_reactions")
@CompoundIndexes({
        @CompoundIndex(name = "couple_place_reaction_unique_idx", def = "{ 'placeId': 1, 'userId': 1, 'type': 1 }", unique = true)
})
public class CouplePlaceReaction {

    @Id
    @JsonProperty("_id")
    private Long id;

    private Long placeId;
    private String userId;
    private CouplePlaceReactionType type;

    @CreatedDate
    private Instant createdAt;
}
