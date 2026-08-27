package com.hi.api.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${app.media.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.media.cloudinary.api-key:}") String apiKey,
            @Value("${app.media.cloudinary.api-secret:}") String apiSecret) {
        return new Cloudinary(Map.of(
                "cloud_name", cloudName == null ? "" : cloudName,
                "api_key", apiKey == null ? "" : apiKey,
                "api_secret", apiSecret == null ? "" : apiSecret,
                "secure", true
        ));
    }
}
