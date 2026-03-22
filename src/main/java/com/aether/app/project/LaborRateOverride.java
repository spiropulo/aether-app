package com.aether.app.project;

/**
 * Project-level hourly rate override for a team member (GraphQL / Firestore map entry).
 */
public class LaborRateOverride {

    private String userProfileId;
    private Double hourlyRate;

    public LaborRateOverride() {
    }

    public LaborRateOverride(String userProfileId, Double hourlyRate) {
        this.userProfileId = userProfileId;
        this.hourlyRate = hourlyRate;
    }

    public String getUserProfileId() {
        return userProfileId;
    }

    public void setUserProfileId(String userProfileId) {
        this.userProfileId = userProfileId;
    }

    public Double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(Double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }
}
