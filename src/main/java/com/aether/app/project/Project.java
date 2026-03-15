package com.aether.app.project;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Document(collectionName = "projects")
public class Project {

    @DocumentId
    private String id;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String name;

    private String description;
    private String startDate;
    private String endDate;
    private String status;
    /** PDF upload record ID if this project was created from a PDF estimate. */
    private String sourcePdfUploadId;

    private Instant createdAt;
    private Instant updatedAt;

    public Project() {
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
