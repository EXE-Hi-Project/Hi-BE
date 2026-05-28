package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnectPartnerRequest {
    @NotBlank(message = "Vui lòng nhập mã kết nối")
    private String partnerCode;
}
