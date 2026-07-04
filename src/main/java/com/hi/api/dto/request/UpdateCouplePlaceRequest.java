package com.hi.api.dto.request;

import com.hi.api.model.CouplePlaceCategory;
import lombok.Data;

@Data
public class UpdateCouplePlaceRequest {
    private String name;
    private String description;
    private CouplePlaceCategory category;
    private String address;
}
