package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertDailyQuestionRequest {

    @NotBlank(message = "Nhóm câu hỏi là bắt buộc")
    @Size(max = 60, message = "Nhóm câu hỏi không được vượt quá 60 ký tự")
    private String category;

    @NotBlank(message = "Nội dung câu hỏi là bắt buộc")
    @Size(max = 500, message = "Câu hỏi không được vượt quá 500 ký tự")
    private String prompt;

    private Boolean active;
}
