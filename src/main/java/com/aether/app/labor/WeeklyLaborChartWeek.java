package com.aether.app.labor;

/**
 * Aggregated planned vs actual hours for one week (for charting).
 */
public class WeeklyLaborChartWeek {

    private String weekStart;
    private String weekEnd;
    private String weekLabel;
    private double plannedHours;
    private double actualHours;
    private Double laborEfficiencyPercent;

    public WeeklyLaborChartWeek() {
    }

    public WeeklyLaborChartWeek(String weekStart, String weekEnd, String weekLabel,
                                double plannedHours, double actualHours, Double laborEfficiencyPercent) {
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.weekLabel = weekLabel;
        this.plannedHours = plannedHours;
        this.actualHours = actualHours;
        this.laborEfficiencyPercent = laborEfficiencyPercent;
    }

    public String getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(String weekStart) {
        this.weekStart = weekStart;
    }

    public String getWeekEnd() {
        return weekEnd;
    }

    public void setWeekEnd(String weekEnd) {
        this.weekEnd = weekEnd;
    }

    public String getWeekLabel() {
        return weekLabel;
    }

    public void setWeekLabel(String weekLabel) {
        this.weekLabel = weekLabel;
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
}
