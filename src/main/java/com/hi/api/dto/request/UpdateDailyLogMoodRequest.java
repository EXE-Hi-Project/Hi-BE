package com.hi.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateDailyLogMoodRequest {

    @Min(value = 1, message = "Mood score phải từ 1 đến 5")
    @Max(value = 5, message = "Mood score phải từ 1 đến 5")
    private Integer moodScore;

    private String notes;
}
