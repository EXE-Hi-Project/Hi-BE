package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateAccountStatusRequest {
    @NotBlank
    private String status;

    private String reason;
}
