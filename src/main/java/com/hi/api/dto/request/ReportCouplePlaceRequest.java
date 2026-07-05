package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportCouplePlaceRequest {
    @NotBlank(message = "Ly do bao cao la bat buoc")
    private String reason;
}
