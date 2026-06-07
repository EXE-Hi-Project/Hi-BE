package com.hi.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class NotificationSettingsRequest {
    private Boolean periodUpcomingEnabled;
    private Boolean fertilityWindowEnabled;
    private Boolean dailyHealthTipsEnabled;
    private Boolean partnerPeriodAlertEnabled;
    private Boolean partnerMoodUpdatesEnabled;
    private Boolean partnerCareTipsEnabled;
    private Boolean pushEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean symptomDailyReminderEnabled;
    private String symptomReminderTime;
    private Boolean partnerEndOfDayNudgeEnabled;
    private String partnerNudgeTime;
    private String aiResponseStyle;

    @Min(value = 0, message = "Số ngày nhắc trước phải từ 0 đến 10")
    @Max(value = 10, message = "Số ngày nhắc trước phải từ 0 đến 10")
    private Integer reminderDaysBefore;
}
