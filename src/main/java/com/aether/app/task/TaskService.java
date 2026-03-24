package com.aether.app.task;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.aether.app.labor.LaborEfficiencyCalendar;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<PagedResult<Task>> getTasks(String tenantId, String projectId, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return taskRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .collectList()
                .map(all -> {
                    int total = all.size();
                    var items = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
                    return new PagedResult<>(items, total, limit, offset);
                });
    }

    public Mono<Task> getTask(String id, String tenantId, String projectId) {
        return taskRepository.findByIdAndProjectIdAndTenantId(id, projectId, tenantId);
    }

    /**
     * Creates a task, or returns the existing task if the same name already exists in the project.
     * Idempotent by (tenantId, projectId, name) so PDF/agents can safely re-call createTask when re-importing.
     */
    public Mono<Task> createTask(CreateTaskInput input) {
        String name = input.getName();
        if (name == null || name.isBlank()) {
            return Mono.error(new IllegalArgumentException("Task name is required."));
        }
        return taskRepository.findAllByTenantIdAndProjectIdAndName(
                        input.getTenantId(), input.getProjectId(), name)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    Task task = new Task();
                    task.setTenantId(input.getTenantId());
                    task.setProjectId(input.getProjectId());
                    task.setName(name);
                    task.setDescription(input.getDescription());
                    task.setAssigneeIds(input.getAssigneeIds());
                    task.setStartDate(input.getStartDate());
                    task.setEndDate(input.getEndDate());
                    task.setCalendarColor(input.getCalendarColor());
                    task.setCalendarExcludedDates(sanitizeCalendarExcludedDates(
                            input.getStartDate(), input.getEndDate(), input.getCalendarExcludedDates()));
                    Instant now = Instant.now();
                    task.setCreatedAt(now);
                    task.setUpdatedAt(now);
                    return taskRepository.save(task);
                }));
    }

    public Mono<Task> updateTask(String id, String tenantId, String projectId, UpdateTaskInput input) {
        return taskRepository.findByIdAndProjectIdAndTenantId(id, projectId, tenantId)
                .flatMap(existing -> {
                    if (input.getName() != null && !input.getName().equals(existing.getName())) {
                        String newName = input.getName();
                        return taskRepository.findAllByTenantIdAndProjectIdAndName(tenantId, projectId, newName)
                                .filter(t -> !t.getId().equals(id))
                                .hasElements()
                                .flatMap(duplicateExists -> {
                                    if (Boolean.TRUE.equals(duplicateExists)) {
                                        return Mono.<Task>error(new DuplicateTaskNameException(projectId, newName));
                                    }
                                    applyUpdates(existing, input);
                                    return taskRepository.save(existing);
                                });
                    }
                    applyUpdates(existing, input);
                    return taskRepository.save(existing);
                });
    }

    private void applyUpdates(Task existing, UpdateTaskInput input) {
        if (input.getName() != null) {
            existing.setName(input.getName());
        }
        if (input.getDescription() != null) {
            existing.setDescription(input.getDescription());
        }
        if (input.getAssigneeIds() != null) {
            existing.setAssigneeIds(input.getAssigneeIds());
        }
        boolean startOrEndChanged = false;
        if (input.getStartDate() != null) {
            existing.setStartDate(input.getStartDate());
            startOrEndChanged = true;
        }
        if (input.getEndDate() != null) {
            existing.setEndDate(input.getEndDate());
            startOrEndChanged = true;
        }
        if (input.getCalendarColor() != null) {
            existing.setCalendarColor(input.getCalendarColor());
        }
        if (input.getCalendarExcludedDates() != null) {
            existing.setCalendarExcludedDates(sanitizeCalendarExcludedDates(
                    existing.getStartDate(), existing.getEndDate(), input.getCalendarExcludedDates()));
        } else if (startOrEndChanged) {
            existing.setCalendarExcludedDates(sanitizeCalendarExcludedDates(
                    existing.getStartDate(), existing.getEndDate(), existing.getCalendarExcludedDates()));
        }
        existing.setUpdatedAt(Instant.now());
    }

    /**
     * Keeps only ISO dates that fall within the task span (inclusive). Null or empty → null (cleared).
     */
    static List<String> sanitizeCalendarExcludedDates(String startIso, String endIso, List<String> raw) {
        if (raw == null) {
            return null;
        }
        LocalDate ts = LaborEfficiencyCalendar.parseIsoDate(startIso);
        LocalDate te = LaborEfficiencyCalendar.parseIsoDate(endIso);
        if (ts == null || te == null) {
            return null;
        }
        LocalDate rangeStart = ts;
        LocalDate rangeEnd = te;
        if (rangeEnd.isBefore(rangeStart)) {
            LocalDate tmp = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = tmp;
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String t = s.trim();
            LocalDate d = LaborEfficiencyCalendar.parseIsoDate(t);
            if (d != null && !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    public Mono<Boolean> deleteTask(String id, String tenantId, String projectId) {
        return taskRepository.findByIdAndProjectIdAndTenantId(id, projectId, tenantId)
                .flatMap(existing -> taskRepository.delete(existing).thenReturn(true));
    }
}
