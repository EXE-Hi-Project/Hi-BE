package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresignAvatarUploadRequest {

    @NotBlank(message = "Ten file la bat buoc")
    private String fileName;

    @NotBlank(message = "Content type la bat buoc")
    private String contentType;

    @NotNull(message = "Kich thuoc file la bat buoc")
    private Long contentLength;
}
