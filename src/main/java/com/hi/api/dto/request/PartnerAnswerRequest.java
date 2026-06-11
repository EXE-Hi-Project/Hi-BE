package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerAnswerRequest {

    @NotBlank(message = "Câu trả lời không được để trống")
    @Size(max = 2000, message = "Câu trả lời tối đa 2.000 ký tự")
    private String content;
}
