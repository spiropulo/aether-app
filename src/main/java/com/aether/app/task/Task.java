package com.aether.app.task;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Document(collectionName = "tasks")
public class Task {

    @DocumentId
    private String id;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String projectId;

    @NotBlank
    private String name;

    private String description;

    /** User profile IDs of assigned team members. */
    private java.util.List<String> assigneeIds;

    /** Start date for calendar (ISO date string). */
    private String startDate;

    /** End date for calendar (ISO date string). */
    private String endDate;

    /** Calendar display color (hex or preset name). */
    private String calendarColor;

    /**
     * ISO dates (YYYY-MM-DD) within {@link #startDate}–{@link #endDate} excluded from the calendar
     * and from labor scheduled-day counts (planned and actual efficiency).
     */
    private java.util.List<String> calendarExcludedDates;

    private Instant createdAt;
    private Instant updatedAt;

    public Task() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public java.util.List<String> getAssigneeIds() {
        return assigneeIds;
    }

    public void setAssigneeIds(java.util.List<String> assigneeIds) {
        this.assigneeIds = assigneeIds;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getCalendarColor() {
        return calendarColor;
    }

    public void setCalendarColor(String calendarColor) {
        this.calendarColor = calendarColor;
    }

    public java.util.List<String> getCalendarExcludedDates() {
        return calendarExcludedDates;
    }

    public void setCalendarExcludedDates(java.util.List<String> calendarExcludedDates) {
        this.calendarExcludedDates = calendarExcludedDates;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
