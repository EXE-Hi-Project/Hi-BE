package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class TrackEventRequest {

    @NotBlank(message = "sessionId không được để trống")
    @Size(max = 80, message = "sessionId quá dài")
    private String sessionId;

    private String userId;

    @NotBlank(message = "eventType không được để trống")
    @Pattern(regexp = "PAGE_VIEW|CLICK|REGISTER|ONBOARDING_COMPLETE", message = "eventType không hợp lệ")
    private String eventType; // PAGE_VIEW, CLICK, REGISTER, ONBOARDING_COMPLETE

    @Size(max = 160, message = "target quá dài")
    private String target; // path or element ID

    @Size(max = 120, message = "elementText quá dài")
    private String elementText; // text on the element

    private Map<String, Object> metadata;
}
