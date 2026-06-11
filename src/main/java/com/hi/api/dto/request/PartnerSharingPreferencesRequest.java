package com.hi.api.dto.request;

import lombok.Data;

@Data
public class PartnerSharingPreferencesRequest {
    private Boolean shareDetailedSymptoms;
    private Boolean shareHealthNotes;
    private Boolean shareMood;
    private Boolean shareCycleData;
    private Boolean dailyQuestionsEnabled;
    private Boolean contextualCareSuggestionsEnabled;
}
