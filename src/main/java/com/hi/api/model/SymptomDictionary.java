package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "symptom_dictionaries")
@CompoundIndexes({
        @CompoundIndex(name = "symptom_dictionary_category_name_idx", def = "{ 'category': 1, 'name': 1 }", unique = true)
})
public class SymptomDictionary {

    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    @Indexed
    private SymptomCategory category;

    private String iconUrl = "";

    private Boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}