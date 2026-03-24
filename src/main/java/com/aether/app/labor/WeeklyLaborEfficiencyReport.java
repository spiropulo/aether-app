package com.aether.app.labor;

import java.util.List;

public class WeeklyLaborEfficiencyReport {

    private String weekStart;
    private String weekEnd;
    private String weekLabel;
    private String timezone;
    private boolean laborConfigComplete;
    private String laborConfigWarning;
    private double plannedHours;
    private double actualHours;
    private Double laborEfficiencyPercent;
    private int completedOfferLines;
    private List<WeeklyLaborEfficiencyDetailRow> detailRows;
    private List<WeeklyLaborChartWeek> chartWeeks;

    public WeeklyLaborEfficiencyReport() {
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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isLaborConfigComplete() {
        return laborConfigComplete;
    }

    public void setLaborConfigComplete(boolean laborConfigComplete) {
        this.laborConfigComplete = laborConfigComplete;
    }

    public String getLaborConfigWarning() {
        return laborConfigWarning;
    }

    public void setLaborConfigWarning(String laborConfigWarning) {
        this.laborConfigWarning = laborConfigWarning;
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

    public int getCompletedOfferLines() {
        return completedOfferLines;
    }

    public void setCompletedOfferLines(int completedOfferLines) {
        this.completedOfferLines = completedOfferLines;
    }

    public List<WeeklyLaborEfficiencyDetailRow> getDetailRows() {
        return detailRows;
    }

    public void setDetailRows(List<WeeklyLaborEfficiencyDetailRow> detailRows) {
        this.detailRows = detailRows;
    }

    public List<WeeklyLaborChartWeek> getChartWeeks() {
        return chartWeeks;
    }

    public void setChartWeeks(List<WeeklyLaborChartWeek> chartWeeks) {
        this.chartWeeks = chartWeeks;
    }
}
