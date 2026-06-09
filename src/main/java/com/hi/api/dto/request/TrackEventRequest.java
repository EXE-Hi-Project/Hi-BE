package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class TrackEventRequest {

    @NotBlank(message = "sessionId không được để trống")
    private String sessionId;

    private String userId;

    @NotBlank(message = "eventType không được để trống")
    private String eventType; // PAGE_VIEW, CLICK, REGISTER, ONBOARDING_COMPLETE

    private String target; // path or element ID

    private String elementText; // text on the element

    private Map<String, Object> metadata;
}
