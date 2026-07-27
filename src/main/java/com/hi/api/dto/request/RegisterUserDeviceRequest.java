package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserDeviceRequest {
    @NotBlank(message = "Device ID là bắt buộc")
    @Size(max = 160, message = "Device ID không hợp lệ")
    private String deviceId;

    @NotBlank(message = "Nền tảng là bắt buộc")
    @Pattern(regexp = "^(ios|android)$", message = "Nền tảng không hợp lệ")
    private String platform;

    @NotBlank(message = "Expo push token là bắt buộc")
    @Size(max = 255, message = "Push token không hợp lệ")
    private String expoPushToken;

    @Size(max = 40, message = "Phiên bản ứng dụng không hợp lệ")
    private String appVersion;
}
