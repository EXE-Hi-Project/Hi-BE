package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSymptomRequest {
    @NotBlank(message = "Tên triệu chứng là bắt buộc")
    private String name;
    private Integer severity;
    private String date;
    private String notes;
}
