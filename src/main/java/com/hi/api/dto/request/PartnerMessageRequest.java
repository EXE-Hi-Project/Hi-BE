package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerMessageRequest {

    @NotBlank(message = "Tin nhắn không được để trống")
    @Size(max = 1000, message = "Tin nhắn tối đa 1.000 ký tự")
    private String content;
}
