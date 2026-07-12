package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmAvatarUploadRequest {

    @NotBlank(message = "Object key la bat buoc")
    private String objectKey;
}
