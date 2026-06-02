package com.hi.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 60, message = "Tên phải từ 2 đến 60 ký tự")
    private String name;

    @Pattern(regexp = "^(female|male|other)$", message = "Giới tính không hợp lệ")
    private String gender;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Ngày sinh phải có định dạng yyyy-MM-dd")
    private String birthDate;

    @DecimalMin(value = "50.0", message = "Chiều cao phải từ 50cm")
    @DecimalMax(value = "250.0", message = "Chiều cao không vượt quá 250cm")
    private Double height;

    @DecimalMin(value = "20.0", message = "Cân nặng phải từ 20kg")
    @DecimalMax(value = "300.0", message = "Cân nặng không vượt quá 300kg")
    private Double weight;

    @Size(max = 20, message = "Tối đa 20 sở thích")
    private List<String> interests;

    @Size(max = 10, message = "Tối đa 10 mục tiêu")
    private List<String> goals;

    @Min(value = 10, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    @Max(value = 90, message = "Độ dài chu kỳ phải từ 10 đến 90 ngày")
    private Integer defaultCycleLength;

    @Min(value = 1, message = "Độ dài kỳ kinh phải từ 1 đến 30 ngày")
    @Max(value = 30, message = "Độ dài kỳ kinh phải từ 1 đến 30 ngày")
    private Integer defaultPeriodLength;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Ngày bắt đầu kỳ kinh phải có định dạng yyyy-MM-dd")
    private String lastPeriodDate;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Ngày kết thúc kỳ kinh phải có định dạng yyyy-MM-dd")
    private String lastPeriodEndDate;
    private Boolean irregularCycle;

    @Pattern(regexp = "^(friendly|professional|caring|playful)$", message = "Tính cách AI không hợp lệ")
    private String aiPersonality;

    @Pattern(regexp = "^(warm|casual|formal)$", message = "Giọng điệu AI không hợp lệ")
    private String aiTone;
    private Boolean periodReminder;

    @Min(value = 0, message = "Số ngày nhắc trước phải từ 0 đến 10")
    @Max(value = 10, message = "Số ngày nhắc trước phải từ 0 đến 10")
    private Integer reminderDaysBefore;
    private Boolean partnerNotifications;
    private Boolean onboardingCompleted;
}
