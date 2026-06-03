package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserNotificationRequest {
    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String message;

    private String type = "ADMIN_MESSAGE";
}
