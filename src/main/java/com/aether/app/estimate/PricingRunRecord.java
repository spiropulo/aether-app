package com.aether.app.estimate;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.time.Instant;

/**
 * Record of an agentic pricing run for a project. Stores the agent report,
 * tool call log, and activity log for UI visibility.
 */
@Document(collectionName = "pricingRuns")
public class PricingRunRecord {

    @DocumentId
    private String id;

    private String tenantId;
    private String projectId;
    /** Agent's summary report (plain text or markdown). */
    private String agentReport;
    /** JSON: tool call log entries. */
    private String toolCallLog;
    /** JSON: agent activity log (stages, timestamps). */
    private String agentActivityLog;
    private Integer toolCallsMade;
    private Instant runAt;
    /** JSON: snapshot of offers (name, quantity, unitCost, total) at time of run. */
    private String offersSnapshot;
    /** Human-readable report: high-level summary and pricing decisions. */
    private String report;

    public PricingRunRecord() {
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
