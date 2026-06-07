package com.hi.api.dto.request;

import com.hi.api.model.HealthVideoStatus;
import com.hi.api.model.HealthVideoTargetAudience;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpsertHealthVideoRequest {

    @NotBlank(message = "YouTube URL hoặc video ID là bắt buộc")
    private String youtubeVideoId;

    @NotBlank(message = "Tiêu đề là bắt buộc")
    private String title;

    private String description;

    @NotBlank(message = "Tên kênh là bắt buộc")
    private String channelName;

    private List<String> topicTags;
    private List<String> interestTags;
    private List<String> goalTags;
    private List<String> phaseTags;
    private String language;
    private Integer priority;
    private HealthVideoStatus status;
    private HealthVideoTargetAudience targetAudience;
}
