package com.aether.app.estimate;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.time.Instant;

@Document(collectionName = "pdfUploads")
public class PdfUploadRecord {

    @DocumentId
    private String id;

    private String tenantId;
    private String uploadedBy;
    private String fileName;
    private String gcsPath;
    private String contentType;
    private long fileSizeBytes;
    private UploadStatus status = UploadStatus.PENDING;
    private Instant uploadedAt;
    /** Project ID (created before AI processing; scopes all mutations). */
    private String projectId;
    /** JSON: training data used for this run (custom entries, for UI display). */
    private String runContext;
    /** JSON: agent activity log (stages, timestamps, messages) for UI visibility. */
    private String agentActivityLog;

    public PdfUploadRecord() {
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

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getGcsPath() {
        return gcsPath;
    }

    public void setGcsPath(String gcsPath) {
        this.gcsPath = gcsPath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRunContext() {
        return runContext;
    }

    public void setRunContext(String runContext) {
        this.runContext = runContext;
    }

    public String getAgentActivityLog() {
        return agentActivityLog;
    }

    public void setAgentActivityLog(String agentActivityLog) {
        this.agentActivityLog = agentActivityLog;
    }
}
