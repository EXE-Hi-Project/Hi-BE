package com.hi.api.dto.request;

import com.hi.api.model.SymptomSeverity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DailyLogSymptomRequest {

    @NotNull(message = "Mã triệu chứng là bắt buộc")
    private Long symptomId;

    private SymptomSeverity severity;
}