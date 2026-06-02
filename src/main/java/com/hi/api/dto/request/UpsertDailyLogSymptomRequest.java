package com.hi.api.dto.request;

import com.hi.api.model.SymptomSeverity;
import lombok.Data;

@Data
public class UpsertDailyLogSymptomRequest {
    private SymptomSeverity severity;
    private String notes;
}
