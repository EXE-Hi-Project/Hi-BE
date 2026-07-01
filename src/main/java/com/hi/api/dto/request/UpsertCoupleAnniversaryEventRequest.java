package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpsertCoupleAnniversaryEventRequest {
    @NotNull(message = "Ngày kỷ niệm không được để trống")
    private LocalDate eventDate;

    @NotBlank(message = "Tiêu đề kỷ niệm không được để trống")
    @Size(max = 120, message = "Tiêu đề tối đa 120 ký tự")
    private String title;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    private String color;
    private String effect;
    private String icon;
    private String sticker;
}
