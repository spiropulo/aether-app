package com.aether.app.estimate;

import java.time.Instant;

/**
 * GraphQL DTO for a pricing run. Mirrors the PricingRun schema type.
 */
public class PricingRun {

    private String id;
    private String tenantId;
    private String projectId;
    private String agentReport;
    private String toolCallLog;
    private String agentActivityLog;
    private Integer toolCallsMade;
    private Instant runAt;
    private String offersSnapshot;
    private String report;

    public static PricingRun from(PricingRunRecord r) {
        PricingRun p = new PricingRun();
        p.setId(r.getId());
        p.setTenantId(r.getTenantId());
        p.setProjectId(r.getProjectId());
        p.setAgentReport(r.getAgentReport());
        p.setToolCallLog(r.getToolCallLog());
        p.setAgentActivityLog(r.getAgentActivityLog());
        p.setToolCallsMade(r.getToolCallsMade());
        p.setRunAt(r.getRunAt());
        p.setOffersSnapshot(r.getOffersSnapshot());
        p.setReport(r.getReport());
        return p;
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

    public String getAgentReport() {
        return agentReport;
    }

    public void setAgentReport(String agentReport) {
        this.agentReport = agentReport;
    }

    public String getToolCallLog() {
        return toolCallLog;
    }

    public void setToolCallLog(String toolCallLog) {
        this.toolCallLog = toolCallLog;
    }

    public String getAgentActivityLog() {
        return agentActivityLog;
    }

    public void setAgentActivityLog(String agentActivityLog) {
        this.agentActivityLog = agentActivityLog;
    }

    public Integer getToolCallsMade() {
        return toolCallsMade;
    }

    public void setToolCallsMade(Integer toolCallsMade) {
        this.toolCallsMade = toolCallsMade;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public String getOffersSnapshot() {
        return offersSnapshot;
    }

    public void setOffersSnapshot(String offersSnapshot) {
        this.offersSnapshot = offersSnapshot;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }
}
