package com.aether.app.estimate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Billable days from task calendar dates (inclusive calendar days, not business-day adjusted).
 */
public final class CalendarWorkdays {

    private CalendarWorkdays() {
    }

    /**
     * Inclusive day count between start and end. Missing dates default to the other; both missing → 1.
     * If end is before start, dates are swapped.
     */
    public static int inclusiveCalendarDays(String startIso, String endIso) {
        LocalDate start = parseLocalDate(startIso);
        LocalDate end = parseLocalDate(endIso);
        if (start == null && end == null) {
            return 1;
        }
        if (start == null) {
            start = end;
        }
        if (end == null) {
            end = start;
        }
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String d = s.trim().split("T")[0];
        try {
            return LocalDate.parse(d);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
