package com.hi.api.dto.request;

import com.hi.api.model.FlowIntensity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpsertDailyLogRequest {

    private FlowIntensity flowIntensity = FlowIntensity.NONE;

    @Min(value = 1, message = "Mood score phải từ 1 đến 5")
    @Max(value = 5, message = "Mood score phải từ 1 đến 5")
    private Integer moodScore;

    private String notes;

    @Valid
    private List<DailyLogSymptomRequest> symptoms = new ArrayList<>();
}