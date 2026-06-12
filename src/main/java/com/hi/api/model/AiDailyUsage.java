package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "ai_daily_usage")
@CompoundIndexes({
        @CompoundIndex(name = "ai_daily_usage_user_date_idx", def = "{ 'userId': 1, 'usageDate': 1 }", unique = true)
})
public class AiDailyUsage {

    @Id
    private String id;

    private String userId;

    private LocalDate usageDate;

    private int used;

    private Instant updatedAt;
}
