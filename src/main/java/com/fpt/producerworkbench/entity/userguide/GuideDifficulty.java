package com.fpt.producerworkbench.entity.userguide;

/**
 * Guide difficulty level enum
 */
public enum GuideDifficulty {
    BEGINNER("beginner", "Beginner", "Dành cho người mới bắt đầu"),
    INTERMEDIATE("intermediate", "Intermediate", "Dành cho người có kinh nghiệm"),
    ADVANCED("advanced", "Advanced", "Dành cho người chuyên nghiệp");
    
    private final String value;
    private final String displayName;
    private final String vietnameseDescription;
    
    GuideDifficulty(String value, String displayName, String vietnameseDescription) {
        this.value = value;
        this.displayName = displayName;
        this.vietnameseDescription = vietnameseDescription;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getVietnameseDescription() {
        return vietnameseDescription;
    }
    
    public static GuideDifficulty fromValue(String value) {
        for (GuideDifficulty difficulty : values()) {
            if (difficulty.value.equals(value)) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Unknown difficulty: " + value);
    }
}
