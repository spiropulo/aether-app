package com.aether.app.labor;

import com.aether.app.offer.Offer;
import com.aether.app.offer.OfferRepository;
import com.aether.app.project.Project;
import com.aether.app.project.ProjectRepository;
import com.aether.app.task.Task;
import com.aether.app.task.TaskRepository;
import com.aether.app.tenant.Tenant;
import com.aether.app.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weekly labor efficiency from task calendar dates and offer {@link Offer#getWorkCompletedAt()} completions.
 * Planned hours prorate every calendar day in the overlap of task start/end with each week (× hours per workday);
 * actual hours use the same day count from task start through each completion instant (per task, max over completions
 * in that week). Task calendar exclusions remove days.
 */
@Service
public class WeeklyLaborEfficiencyService {

    /** Weeks of history ending at the selected week (inclusive). Larger window supports horizontal scroll in the UI. */
    private static final int CHART_WEEK_COUNT = 26;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final TaskRepository taskRepository;
    private final OfferRepository offerRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;

    public WeeklyLaborEfficiencyService(TaskRepository taskRepository,
                                        OfferRepository offerRepository,
                                        TenantRepository tenantRepository,
                                        ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.offerRepository = offerRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
    }

    public static LocalDate startOfWeekContaining(LocalDate anchor, WeekStartMode mode) {
        return switch (mode) {
            case ISO_MONDAY -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case US_SUNDAY -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        };
    }

    public Mono<WeeklyLaborEfficiencyReport> weeklyLaborEfficiency(String tenantId,
                                                                   String projectId,
                                                                   String weekContainingDate,
                                                                   WeekStartMode weekStartMode,
                                                                   String assigneeId,
                                                                   String taskId) {
        LocalDate anchor = LocalDate.parse(weekContainingDate.trim(), ISO_DATE);
        LocalDate selectedWeekStart = startOfWeekContaining(anchor, weekStartMode);
        String assigneeFilter = assigneeId != null && !assigneeId.isBlank() ? assigneeId.trim() : null;
        String taskFilter = taskId != null && !taskId.isBlank() ? taskId.trim() : null;

        return Mono.zip(
                taskRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList(),
                offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList(),
                tenantRepository.findAllByTenantId(tenantId).collectList().map(l -> l.isEmpty() ? null : l.get(0)),
                projectRepository.findByIdAndTenantId(projectId, tenantId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Project not found.")))
        ).map(tuple -> {
            List<Task> tasks = tuple.getT1();
            List<Offer> offers = tuple.getT2().stream()
                    .filter(o -> tenantId.equals(o.getTenantId()))
                    .collect(Collectors.toList());
            Tenant tenant = tuple.getT3();
            Project project = tuple.getT4();

            LaborEfficiencyCalendar.ResolvedConfig cfg = LaborEfficiencyCalendar.resolve(tenant, project);
            ZoneId zone = cfg.getZone();
            double hpd = cfg.getHoursPerWeekday();

            Map<String, Task> taskById = new HashMap<>();
            Map<String, String> taskNames = new HashMap<>();
            for (Task t : tasks) {
                if (t.getId() != null) {
                    taskById.put(t.getId(), t);
                    if (t.getName() != null) {
                        taskNames.put(t.getId(), t.getName());
                    }
                }
            }

            LocalDate selectedWeekEndInc = selectedWeekStart.plusDays(6);
            Instant selectedStart = selectedWeekStart.atStartOfDay(zone).toInstant();
            Instant selectedEnd = selectedWeekStart.plusWeeks(1).atStartOfDay(zone).toInstant();

            List<Offer> selectedWeekOffers = offers.stream()
                    .filter(o -> inWeek(o, selectedStart, selectedEnd))
                    .filter(o -> matchesAssignee(o, assigneeFilter))
                    .filter(o -> matchesTask(o, taskFilter))
                    .sorted(Comparator.comparing(Offer::getWorkCompletedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            Set<String> taskIdsWithCompletion = new HashSet<>();
            for (Offer o : selectedWeekOffers) {
                taskIdsWithCompletion.add(o.getTaskId());
            }

            double sumPlanned = 0.0;
            double sumActual = 0.0;
            for (String tid : taskIdsWithCompletion) {
                Task task = taskById.get(tid);
                if (task == null || !taskIncluded(task, offers, assigneeFilter, taskFilter)) {
                    continue;
                }
                int scheduledDays = LaborEfficiencyCalendar.plannedWeekdayOverlap(task, selectedWeekStart, selectedWeekEndInc);
                sumPlanned += scheduledDays * hpd;
                double maxA = 0.0;
                for (Offer o : selectedWeekOffers) {
                    if (!tid.equals(o.getTaskId())) {
                        continue;
                    }
                    maxA = Math.max(maxA, actualHoursThroughCompletion(task, o.getWorkCompletedAt(), zone, hpd));
                }
                sumActual += maxA;
            }

            List<WeeklyLaborEfficiencyDetailRow> rows = new ArrayList<>();
            for (Offer o : selectedWeekOffers) {
                Task task = taskById.get(o.getTaskId());
                double plannedRow = 0.0;
                double actualRow = 0.0;
                if (task != null && taskIncluded(task, offers, assigneeFilter, taskFilter)) {
                    int scheduledDays = LaborEfficiencyCalendar.plannedWeekdayOverlap(task, selectedWeekStart, selectedWeekEndInc);
                    plannedRow = scheduledDays * hpd;
                    actualRow = actualHoursThroughCompletion(task, o.getWorkCompletedAt(), zone, hpd);
                }
                rows.add(new WeeklyLaborEfficiencyDetailRow(
                        o.getTaskId(),
                        taskNames.getOrDefault(o.getTaskId(), o.getTaskId()),
                        o.getId(),
                        o.getName(),
                        o.getAssigneeIds() != null ? List.copyOf(o.getAssigneeIds()) : List.of(),
                        plannedRow,
                        actualRow,
                        laborEfficiencyPercent(plannedRow, actualRow),
                        o.getWorkCompletedAt() != null
                                ? DateTimeFormatter.ISO_INSTANT.format(o.getWorkCompletedAt())
                                : null
                ));
            }

            WeeklyLaborEfficiencyReport report = new WeeklyLaborEfficiencyReport();
            report.setTimezone(zone.getId());
            report.setLaborConfigComplete(cfg.isLaborConfigComplete());
            report.setLaborConfigWarning(cfg.getLaborConfigWarning());
            report.setWeekStart(iso(selectedWeekStart));
            report.setWeekEnd(iso(selectedWeekEndInc));
            report.setWeekLabel(weekRangeLabel(selectedWeekStart));
            report.setDetailRows(rows);
            report.setCompletedOfferLines(rows.size());
            report.setPlannedHours(sumPlanned);
            report.setActualHours(sumActual);
            report.setLaborEfficiencyPercent(laborEfficiencyPercent(sumPlanned, sumActual));

            List<WeeklyLaborChartWeek> chart = new ArrayList<>();
            LocalDate oldestStart = selectedWeekStart.minusWeeks(CHART_WEEK_COUNT - 1);
            for (int i = 0; i < CHART_WEEK_COUNT; i++) {
                LocalDate ws = oldestStart.plusWeeks(i);
                LocalDate weInc = ws.plusDays(6);
                Instant wStart = ws.atStartOfDay(zone).toInstant();
                Instant wEnd = ws.plusWeeks(1).atStartOfDay(zone).toInstant();

                Set<String> tasksDoneThisWeek = new HashSet<>();
                for (Offer o : offers) {
                    if (!inWeek(o, wStart, wEnd)) {
                        continue;
                    }
                    if (!matchesAssignee(o, assigneeFilter)) {
                        continue;
                    }
                    if (!matchesTask(o, taskFilter)) {
                        continue;
                    }
                    tasksDoneThisWeek.add(o.getTaskId());
                }

                double pSum = 0.0;
                double aSum = 0.0;
                for (String tid : tasksDoneThisWeek) {
                    Task task = taskById.get(tid);
                    if (task == null || !taskIncluded(task, offers, assigneeFilter, taskFilter)) {
                        continue;
                    }
                    int scheduledDays = LaborEfficiencyCalendar.plannedWeekdayOverlap(task, ws, weInc);
                    pSum += scheduledDays * hpd;
                    double maxA = 0.0;
                    for (Offer o : offers) {
                        if (!tid.equals(o.getTaskId()) || !inWeek(o, wStart, wEnd)) {
                            continue;
                        }
                        if (!matchesAssignee(o, assigneeFilter) || !matchesTask(o, taskFilter)) {
                            continue;
                        }
                        maxA = Math.max(maxA, actualHoursThroughCompletion(task, o.getWorkCompletedAt(), zone, hpd));
                    }
                    aSum += maxA;
                }

                WeeklyLaborChartWeek bar = new WeeklyLaborChartWeek();
                bar.setWeekStart(iso(ws));
                bar.setWeekEnd(iso(weInc));
                bar.setWeekLabel(weekRangeLabel(ws));
                bar.setPlannedHours(pSum);
                bar.setActualHours(aSum);
                bar.setLaborEfficiencyPercent(laborEfficiencyPercent(pSum, aSum));
                chart.add(bar);
            }
            report.setChartWeeks(chart);
            return report;
        });
    }

    private static boolean taskIncluded(Task task, List<Offer> allOffers, String assigneeFilter, String taskFilter) {
        String tid = task.getId();
        if (taskFilter != null && !taskFilter.equals(tid)) {
            return false;
        }
        if (assigneeFilter == null) {
            return true;
        }
        for (Offer o : allOffers) {
            if (tid.equals(o.getTaskId()) && matchesAssignee(o, assigneeFilter)) {
                return true;
            }
        }
        return false;
    }

    private static double actualHoursThroughCompletion(Task task, Instant workCompletedAt, ZoneId zone, double hpd) {
        if (workCompletedAt == null) {
            return 0.0;
        }
        LocalDate taskStart = LaborEfficiencyCalendar.parseIsoDate(task.getStartDate());
        if (taskStart == null) {
            return 0.0;
        }
        LocalDate completionDay = workCompletedAt.atZone(zone).toLocalDate();
        int days = LaborEfficiencyCalendar.countScheduledDaysInclusiveWithExclusions(
                taskStart,
                completionDay,
                LaborEfficiencyCalendar.calendarExcludedDatesAsSet(task));
        return days * hpd;
    }

    private static String iso(LocalDate d) {
        return d.format(ISO_DATE);
    }

    private static String weekRangeLabel(LocalDate weekStart) {
        LocalDate last = weekStart.plusDays(6);
        return weekStart.format(LABEL) + " – " + last.format(LABEL);
    }

    private static boolean inWeek(Offer o, Instant start, Instant end) {
        if (!o.isWorkCompleted()) {
            return false;
        }
        Instant w = o.getWorkCompletedAt();
        if (w == null) {
            return false;
        }
        return !w.isBefore(start) && w.isBefore(end);
    }

    private static boolean matchesAssignee(Offer o, String assigneeFilter) {
        if (assigneeFilter == null) {
            return true;
        }
        List<String> ids = o.getAssigneeIds();
        return ids != null && ids.contains(assigneeFilter);
    }

    private static boolean matchesTask(Offer o, String taskFilter) {
        if (taskFilter == null) {
            return true;
        }
        return taskFilter.equals(o.getTaskId());
    }

    /**
     * Planned ÷ actual × 100 when both are positive; otherwise null.
     * Values over 100% mean less time was used than planned (favorable).
     */
    public static Double laborEfficiencyPercent(double plannedHours, double actualHours) {
        if (plannedHours <= 0 || actualHours <= 0) {
            return null;
        }
        return (plannedHours / actualHours) * 100.0;
    }
}
