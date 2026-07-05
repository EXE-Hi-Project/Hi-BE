package com.hi.api.dto.request;

import com.hi.api.model.CouplePlaceCategory;
import com.hi.api.model.CouplePlaceVisibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateCouplePlaceRequest {

    @NotBlank(message = "Ten dia diem la bat buoc")
    private String name;

    private String description;

    @NotNull(message = "Danh muc dia diem la bat buoc")
    private CouplePlaceCategory category;

    @NotNull(message = "Latitude la bat buoc")
    @DecimalMin(value = "-90.0", message = "Latitude khong hop le")
    @DecimalMax(value = "90.0", message = "Latitude khong hop le")
    private Double lat;

    @NotNull(message = "Longitude la bat buoc")
    @DecimalMin(value = "-180.0", message = "Longitude khong hop le")
    @DecimalMax(value = "180.0", message = "Longitude khong hop le")
    private Double lng;

    private String address;
    private String city;
    private String district;
    private String googlePlaceId;
    private Double googleRating;
    private Integer googleUserRatingCount;
    private String googleMapsUri;
    private List<String> tags;
    private Boolean anonymous;
    private String nickname;
    private CouplePlaceVisibility visibility = CouplePlaceVisibility.PUBLIC;
}
