package com.aether.app.estimate;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Async acknowledgment returned immediately after the PDF is uploaded and queued for processing.")
public class PdfUploadAcknowledgment {

    @Schema(description = "Always \"accepted\" on success.", example = "accepted")
    private final String status;

    @Schema(description = "User-friendly message describing next steps.")
    private final String message;

    @Schema(description = "Unique reference ID for this upload. Use it to track processing status.", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String referenceId;

    @Schema(description = "Tenant ID this upload is associated with.", example = "tenant-123")
    private final String tenantId;

    @Schema(description = "Original file name.", example = "estimate-q1.pdf")
    private final String fileName;

    @Schema(description = "File size in bytes.", example = "1048576")
    private final long fileSizeBytes;

    @Schema(description = "GCS path where the file is durably stored.", example = "gs://aether-estimates/estimates/550e8400/estimate-q1.pdf")
    private final String gcsPath;

    @Schema(description = "ISO-8601 timestamp of when the file was uploaded.")
    private final Instant uploadedAt;

    public PdfUploadAcknowledgment(String status, String message, String referenceId,
                                    String tenantId, String fileName, long fileSizeBytes,
                                    String gcsPath, Instant uploadedAt) {
        this.status = status;
        this.message = message;
        this.referenceId = referenceId;
        this.tenantId = tenantId;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.gcsPath = gcsPath;
        this.uploadedAt = uploadedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getGcsPath() {
        return gcsPath;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
