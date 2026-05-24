package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Document(collection = "click_trackings")
@CompoundIndexes({
        @CompoundIndex(name = "click_tracking_product_clicked_idx", def = "{ 'productId': 1, 'clickedAt': -1 }"),
        @CompoundIndex(name = "click_tracking_user_clicked_idx", def = "{ 'userId': 1, 'clickedAt': -1 }")
})
public class ClickTracking {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private String userId;

    @Indexed
    private Long productId;

    @Indexed
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime clickedAt;
}