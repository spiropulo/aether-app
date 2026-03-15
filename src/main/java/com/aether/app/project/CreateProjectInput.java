package com.aether.app.project;

public class CreateProjectInput {

    private String tenantId;
    private String name;
    private String description;
    private String startDate;
    private String endDate;
    private String status;
    private String sourcePdfUploadId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourcePdfUploadId() {
        return sourcePdfUploadId;
    }

    public void setSourcePdfUploadId(String sourcePdfUploadId) {
        this.sourcePdfUploadId = sourcePdfUploadId;
    }
}
