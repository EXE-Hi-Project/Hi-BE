package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    @JsonProperty("_id")
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String authProvider = "local";
    private String googleId;
    private String facebookId;

    private String role = "user";
    private String gender;
    private String avatar = "";

    private String accountStatus = "ACTIVE";
    private String accountStatusReason;
    private Instant accountStatusUpdatedAt;
    private String accountStatusUpdatedBy;

    // Onboarding
    private String birthDate;
    private Double height;
    private Double weight;
    private List<String> interests = new ArrayList<>();
    private List<String> goals = new ArrayList<>();
    private Integer defaultCycleLength = 28;
    private Integer defaultPeriodLength = 5;
    private String lastPeriodDate;
    private String lastPeriodEndDate;
    private Boolean irregularCycle = false;

    // Partner
    private String partnerId;

    @Indexed(unique = true, sparse = true)
    private String partnerCode;

    // AI preferences
    private String aiPersonality = "friendly";
    private String aiTone = "warm";

    // Notification settings
    private Boolean periodReminder = true;
    private Integer reminderDaysBefore = 3;
    private Boolean partnerNotifications = true;
    private NotificationPreferences notificationPreferences = new NotificationPreferences();
    private PartnerSharingPreferences partnerSharingPreferences = new PartnerSharingPreferences();

    // Onboarding
    private Boolean onboardingCompleted = false;

    // Subscription
    private SubscriptionInfo subscription = new SubscriptionInfo();

    @Data
    @NoArgsConstructor
    public static class SubscriptionInfo {
        private String stripeCustomerId;
        private String stripeSubscriptionId;
        private Long payosOrderCode;
        private String plan = "free";
        private String status;
        private Instant currentPeriodEnd;
    }

    @Data
    @NoArgsConstructor
    public static class NotificationPreferences {
        private Boolean periodUpcomingEnabled = true;
        private Boolean fertilityWindowEnabled = false;
        private Boolean dailyHealthTipsEnabled = true;
        private Boolean partnerPeriodAlertEnabled = true;
        private Boolean partnerMoodUpdatesEnabled = true;
        private Boolean partnerCareTipsEnabled = false;
        private Boolean pushEnabled = true;
        private Boolean emailEnabled = true;
        private Boolean smsEnabled = false;
        private Integer reminderDaysBefore = 3;
        private Boolean symptomDailyReminderEnabled = true;
        private String symptomReminderTime = "20:00";
        private Boolean partnerEndOfDayNudgeEnabled = true;
        private String partnerNudgeTime = "21:00";
        private String aiResponseStyle = "FRIENDLY";
        private Boolean dailyQuestionsEnabled = true;
        private Boolean contextualCareSuggestionsEnabled = true;
    }

    @Data
    @NoArgsConstructor
    public static class PartnerSharingPreferences {
        private Boolean shareDetailedSymptoms = false;
        private Boolean shareHealthNotes = false;
        private Boolean shareMood = false;
        private Boolean shareCycleData = false;
        private String consentVersion;
        private Instant consentedAt;
    }

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
