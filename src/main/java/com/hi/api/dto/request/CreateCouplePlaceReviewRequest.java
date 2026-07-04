package com.hi.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCouplePlaceReviewRequest {

    @NotNull(message = "Rating la bat buoc")
    @Min(value = 1, message = "Rating toi thieu la 1")
    @Max(value = 5, message = "Rating toi da la 5")
    private Integer rating;

    private String content;
    private Boolean anonymous;
    private String nickname;
}
