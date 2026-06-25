package com.hi.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateCoupleStartDateRequest {
    @NotNull(message = "Ngy bắt đầu bn nhau khng được để trống")
    private LocalDate startDate;

    private String title;
    private String note;

    private String color;
    private String effect;
    private String icon;
    private String sticker;
}
