package com.aether.app.task;

import java.util.List;

public class UpdateTaskInput {

    private String name;
    private String description;
    private List<String> assigneeIds;
    private String startDate;
    private String endDate;
    private String calendarColor;
    private List<String> calendarExcludedDates;

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

    public List<String> getAssigneeIds() {
        return assigneeIds;
    }

    public void setAssigneeIds(List<String> assigneeIds) {
        this.assigneeIds = assigneeIds;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getCalendarColor() {
        return calendarColor;
    }

    public void setCalendarColor(String calendarColor) {
        this.calendarColor = calendarColor;
    }

    public List<String> getCalendarExcludedDates() {
        return calendarExcludedDates;
    }

    public void setCalendarExcludedDates(List<String> calendarExcludedDates) {
        this.calendarExcludedDates = calendarExcludedDates;
    }
}
