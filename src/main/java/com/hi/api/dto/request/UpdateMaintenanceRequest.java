package com.hi.api.dto.request;

import com.hi.api.model.MaintenanceMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateMaintenanceRequest {
    private boolean enabled;

    @NotNull(message = "Chế độ bảo trì là bắt buộc")
    private MaintenanceMode mode;

    @NotBlank(message = "Tiêu đề bảo trì là bắt buộc")
    @Size(max = 120, message = "Tiêu đề bảo trì tối đa 120 ký tự")
    private String title;

    @NotBlank(message = "Thông điệp bảo trì là bắt buộc")
    @Size(max = 500, message = "Thông điệp bảo trì tối đa 500 ký tự")
    private String message;

    private Instant startsAt;
    private Instant endsAt;
}
