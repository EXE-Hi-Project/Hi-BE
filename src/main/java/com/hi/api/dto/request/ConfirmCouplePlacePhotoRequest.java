package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmCouplePlacePhotoRequest {

    @NotBlank(message = "Object key la bat buoc")
    private String objectKey;

    @NotBlank(message = "URL anh la bat buoc")
    private String url;

    @NotBlank(message = "Content type la bat buoc")
    private String contentType;
}
