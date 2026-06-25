package com.hi.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpsertCoupleAnniversaryEventRequest {
    @NotNull(message = "Ng�y kỷ niệm kh�ng được để trống")
    private LocalDate eventDate;

    @NotBlank(message = "Ti�u đề kỷ niệm kh�ng được để trống")
    @Size(max = 120, message = "Ti�u đề tối đa 120 k� tự")
    private String title;

    @Size(max = 1000, message = "Ghi ch� tối đa 1000 k� tự")
    private String note;

    private String color;
    private String effect;
    private String icon;
    private String sticker;
}
