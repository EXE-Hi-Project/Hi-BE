package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Nội dung tin nhắn là bắt buộc")
    private String content;
    private LocalDate sessionDate;
}
