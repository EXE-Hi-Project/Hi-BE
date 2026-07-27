package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteMyAccountRequest {
    @NotBlank(message = "Vui lòng nhập email để xác nhận")
    private String confirmation;

    private String password;
}
