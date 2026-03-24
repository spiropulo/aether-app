package com.aether.app.labor;

import com.aether.app.project.Project;
import com.aether.app.task.Task;
import com.aether.app.tenant.Tenant;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves tenant/project labor calendar settings and counts scheduled calendar days for planned/actual hours.
 * Each counted day contributes the configured workday length (start→end); weekends count the same unless excluded.
 */
public final class LaborEfficiencyCalendar {

    private LaborEfficiencyCalendar() {
    }

    public static final ZoneId FALLBACK_ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(17, 0);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FLEX_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("H:mm")
            .toFormatter(Locale.ROOT);

    public static final class ResolvedConfig {
        private final ZoneId zone;
        private final double hoursPerWeekday;
        private final boolean laborConfigComplete;
        private final String laborConfigWarning;

        public ResolvedConfig(ZoneId zone, double hoursPerWeekday, boolean laborConfigComplete, String laborConfigWarning) {
            this.zone = zone;
            this.hoursPerWeekday = hoursPerWeekday;
            this.laborConfigComplete = laborConfigComplete;
            this.laborConfigWarning = laborConfigWarning;
        }

        public ZoneId getZone() {
            return zone;
        }

        public double getHoursPerWeekday() {
            return hoursPerWeekday;
        }

        public boolean isLaborConfigComplete() {
            return laborConfigComplete;
        }

        public String getLaborConfigWarning() {
            return laborConfigWarning;
        }
    }

    public static ResolvedConfig resolve(Tenant tenant, Project project) {
        List<String> warnings = new ArrayList<>();
        ZoneId zone = FALLBACK_ZONE;
        boolean tzOk = false;
        if (tenant != null && notBlank(tenant.getLaborTimezone())) {
            try {
                zone = ZoneId.of(tenant.getLaborTimezone().trim());
                tzOk = true;
            } catch (Exception e) {
                warnings.add("Invalid workspace IANA timezone; using America/Los_Angeles.");
            }
        } else {
            warnings.add("Set workspace labor timezone in Settings (IANA, e.g. America/Los_Angeles).");
        }

        String ws = pickWorkdayStart(tenant, project);
        String we = pickWorkdayEnd(tenant, project);
        LocalTime start = DEFAULT_START;
        LocalTime end = DEFAULT_END;
        boolean workdayOk = false;
        if (notBlank(ws) && notBlank(we)) {
            LocalTime s = parseLocalTime(ws);
            LocalTime e = parseLocalTime(we);
            if (s != null && e != null && e.isAfter(s)) {
                start = s;
                end = e;
                workdayOk = true;
            } else {
                warnings.add("Workday start/end invalid or end not after start; using 09:00–17:00.");
            }
        } else {
            warnings.add("Set default workday start and end in Settings (HH:mm). Using 09:00–17:00 until set.");
        }

        double hpd = hoursBetween(start, end);
        boolean complete = tzOk && workdayOk;
        String warning = warnings.isEmpty() ? null : String.join(" ", warnings);
        return new ResolvedConfig(zone, hpd, complete, warning);
    }

    private static String pickWorkdayStart(Tenant tenant, Project project) {
        if (project != null && notBlank(project.getLaborWorkdayStart())) {
            return project.getLaborWorkdayStart().trim();
        }
        if (tenant != null && notBlank(tenant.getLaborWorkdayStart())) {
            return tenant.getLaborWorkdayStart().trim();
        }
        return null;
    }

    private static String pickWorkdayEnd(Tenant tenant, Project project) {
        if (project != null && notBlank(project.getLaborWorkdayEnd())) {
            return project.getLaborWorkdayEnd().trim();
        }
        if (tenant != null && notBlank(tenant.getLaborWorkdayEnd())) {
            return tenant.getLaborWorkdayEnd().trim();
        }
        return null;
    }

    private static LocalTime parseLocalTime(String s) {
        String t = s.trim();
        try {
            return LocalTime.parse(t, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(t, FLEX_TIME);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static double hoursBetween(LocalTime start, LocalTime end) {
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    public static LocalDate parseIsoDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Calendar day count inclusive in [{@code from}, {@code to}] (every day of the week); null dates → 0.
     */
    public static int countCalendarDaysInclusive(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return 0;
        }
        LocalDate a = from;
        LocalDate b = to;
        if (b.isBefore(a)) {
            LocalDate tmp = a;
            a = b;
            b = tmp;
        }
        int c = 0;
        for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
            c++;
        }
        return c;
    }

    /**
     * Parsed excluded ISO dates that lie within the task start–end span (inclusive), in normalized order.
     */
    public static Set<LocalDate> calendarExcludedDatesAsSet(Task task) {
        LocalDate ts = parseIsoDate(task.getStartDate());
        LocalDate te = parseIsoDate(task.getEndDate());
        if (ts == null || te == null) {
            return Set.of();
        }
        LocalDate rangeStart = ts;
        LocalDate rangeEnd = te;
        if (rangeEnd.isBefore(rangeStart)) {
            LocalDate tmp = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = tmp;
        }
        Set<LocalDate> out = new HashSet<>();
        List<String> raw = task.getCalendarExcludedDates();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            LocalDate d = parseIsoDate(s);
            if (d != null && !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Calendar days inclusive in [{@code from}, {@code to}], skipping dates in {@code excluded} (e.g. task calendar removals).
     */
    public static int countScheduledDaysInclusiveWithExclusions(LocalDate from, LocalDate to, Set<LocalDate> excluded) {
        if (from == null || to == null) {
            return 0;
        }
        LocalDate a = from;
        LocalDate b = to;
        if (b.isBefore(a)) {
            LocalDate tmp = a;
            a = b;
            b = tmp;
        }
        Set<LocalDate> ex = excluded != null ? excluded : Set.of();
        int c = 0;
        for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
            if (!ex.contains(d)) {
                c++;
            }
        }
        return c;
    }

    /**
     * Count of scheduled calendar days where the task range overlaps the reporting week (inclusive on both ends),
     * minus {@link Task#getCalendarExcludedDates()} in that intersection. Each day maps to the configured workday hours.
     */
    public static int plannedWeekdayOverlap(Task task, LocalDate weekStart, LocalDate weekEndInclusive) {
        LocalDate ts = parseIsoDate(task.getStartDate());
        LocalDate te = parseIsoDate(task.getEndDate());
        if (ts == null || te == null) {
            return 0;
        }
        LocalDate rangeStart = ts.isAfter(weekStart) ? ts : weekStart;
        LocalDate rangeEnd = te.isBefore(weekEndInclusive) ? te : weekEndInclusive;
        if (rangeEnd.isBefore(rangeStart)) {
            return 0;
        }
        return countScheduledDaysInclusiveWithExclusions(rangeStart, rangeEnd, calendarExcludedDatesAsSet(task));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
