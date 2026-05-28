package com.hi.api.dto.request;

import com.hi.api.model.SymptomCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertSymptomDictionaryRequest {

    @NotBlank(message = "Tên triệu chứng là bắt buộc")
    private String name;

    @NotNull(message = "Danh mục triệu chứng là bắt buộc")
    private SymptomCategory category;

    private String iconUrl;

    private Boolean active;
}