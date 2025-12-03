package com.fpt.producerworkbench.entity.userguide;

/**
 * Guide category enum
 */
public enum GuideCategory {
    GETTING_STARTED("getting-started", "Getting Started"),
    PROJECT_MANAGEMENT("project-management", "Project Management"),
    COLLABORATION("collaboration", "Collaboration"),
    AUDIO_PRODUCTION("audio-production", "Audio Production"),
    TRACK_MANAGEMENT("track-management", "Track Management"),
    PAYMENT_BILLING("payment-billing", "Payment & Billing"),
    TROUBLESHOOTING("troubleshooting", "Troubleshooting"),
    ACCOUNT_SETTINGS("account-settings", "Account Settings");
    
    private final String value;
    private final String displayName;
    
    GuideCategory(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static GuideCategory fromValue(String value) {
        for (GuideCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + value);
    }
}
