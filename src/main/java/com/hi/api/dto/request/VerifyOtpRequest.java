package com.hi.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã OTP là bắt buộc")
    @Size(min = 6, max = 6, message = "Mã OTP phải đúng 6 ký tự")
    private String otp;
}
