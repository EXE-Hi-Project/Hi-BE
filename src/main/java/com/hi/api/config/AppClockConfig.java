package com.hi.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppClockConfig {

    @Bean
    public Clock appClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
