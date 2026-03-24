package com.aether.app.labor;

import java.util.List;

/**
 * One completed offer line included in a weekly labor report.
 */
public class WeeklyLaborEfficiencyDetailRow {

    private String taskId;
    private String taskName;
    private String offerId;
    private String offerName;
    private List<String> assigneeIds;
    private double plannedHours;
    private double actualHours;
    private Double laborEfficiencyPercent;
    private String workCompletedAt;

    public WeeklyLaborEfficiencyDetailRow() {
    }

    public WeeklyLaborEfficiencyDetailRow(String taskId, String taskName, String offerId, String offerName,
                                          List<String> assigneeIds, double plannedHours, double actualHours,
                                          Double laborEfficiencyPercent, String workCompletedAt) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.offerId = offerId;
        this.offerName = offerName;
        this.assigneeIds = assigneeIds;
        this.plannedHours = plannedHours;
        this.actualHours = actualHours;
        this.laborEfficiencyPercent = laborEfficiencyPercent;
        this.workCompletedAt = workCompletedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getOfferName() {
        return offerName;
    }

    public void setOfferName(String offerName) {
        this.offerName = offerName;
    }

    public List<String> getAssigneeIds() {
        return assigneeIds;
    }

    public void setAssigneeIds(List<String> assigneeIds) {
        this.assigneeIds = assigneeIds;
    }

    public double getPlannedHours() {
        return plannedHours;
    }

    public void setPlannedHours(double plannedHours) {
        this.plannedHours = plannedHours;
    }

    public double getActualHours() {
        return actualHours;
    }

    public void setActualHours(double actualHours) {
        this.actualHours = actualHours;
    }

    public Double getLaborEfficiencyPercent() {
        return laborEfficiencyPercent;
    }

    public void setLaborEfficiencyPercent(Double laborEfficiencyPercent) {
        this.laborEfficiencyPercent = laborEfficiencyPercent;
    }

    public String getWorkCompletedAt() {
        return workCompletedAt;
    }

    public void setWorkCompletedAt(String workCompletedAt) {
        this.workCompletedAt = workCompletedAt;
    }
}
