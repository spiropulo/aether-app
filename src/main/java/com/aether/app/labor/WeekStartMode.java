package com.aether.app.labor;

/**
 * How the first day of a calendar week is chosen when normalizing {@code weekContainingDate}.
 */
public enum WeekStartMode {
    /** Week starts Monday (ISO-8601). */
    ISO_MONDAY,
    /** Week starts Sunday (common US convention). */
    US_SUNDAY
}
