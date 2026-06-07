package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "health_videos")
@CompoundIndexes({
        @CompoundIndex(name = "health_video_youtube_id_idx", def = "{ 'youtubeVideoId': 1 }", unique = true),
        @CompoundIndex(name = "health_video_status_priority_idx", def = "{ 'status': 1, 'priority': -1 }")
})
public class HealthVideo {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private String youtubeVideoId;

    private String title;
    private String description = "";
    private String channelName;
    private String sourceUrl;
    private String thumbnailUrl;
    private List<String> topicTags = new ArrayList<>();
    private List<String> interestTags = new ArrayList<>();
    private List<String> goalTags = new ArrayList<>();
    private List<String> phaseTags = new ArrayList<>();
    private String language = "vi";
    private Integer priority = 0;

    @Indexed
    private HealthVideoStatus status = HealthVideoStatus.DRAFT;

    @Indexed
    private HealthVideoTargetAudience targetAudience = HealthVideoTargetAudience.BOTH;

    private Instant reviewedAt;
    private String reviewedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
