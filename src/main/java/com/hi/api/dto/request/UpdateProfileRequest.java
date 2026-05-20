package com.hi.api.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileRequest {
    private String name;
    private String gender;
    private String birthDate;
    private Double height;
    private Double weight;
    private List<String> interests;
    private List<String> goals;
    private Integer defaultCycleLength;
    private Integer defaultPeriodLength;
    private String lastPeriodDate;
    private String lastPeriodEndDate;
    private Boolean irregularCycle;
    private String aiPersonality;
    private String aiTone;
    private Boolean periodReminder;
    private Integer reminderDaysBefore;
    private Boolean partnerNotifications;
    private Boolean onboardingCompleted;
}
