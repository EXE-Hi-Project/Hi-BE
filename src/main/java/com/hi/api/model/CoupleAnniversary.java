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

@Data
@NoArgsConstructor
@Document(collection = "couple_anniversaries")
@CompoundIndexes({
        @CompoundIndex(name = "couple_anniversary_pair_type_idx", def = "{ 'pairKey': 1, 'type': 1 }"),
        @CompoundIndex(name = "couple_anniversary_pair_date_idx", def = "{ 'pairKey': 1, 'eventDate': 1 }")
})
public class CoupleAnniversary {

    public enum Type {
        START_DATE,
        MEMORY
    }

    @Id
    @JsonProperty("_id")
    private String id;

    private String pairKey;
    private Type type;
    private LocalDate eventDate;
    private String title;
    private String note;
    private String color;
    private String effect;
    private String icon;
    private String sticker;
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
