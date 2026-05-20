package com.hi.api.dto.request;

import lombok.Data;

@Data
public class GoogleAuthRequest {
    private String credential;
    private String accessToken;
}
