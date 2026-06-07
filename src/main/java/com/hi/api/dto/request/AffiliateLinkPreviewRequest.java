package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AffiliateLinkPreviewRequest {
    @NotBlank(message = "Link affiliate không được để trống")
    private String url;
}
