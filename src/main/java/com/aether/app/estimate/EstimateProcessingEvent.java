package com.aether.app.estimate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Payload of the Pub/Sub message published when a PDF is uploaded.
 * projectId is set when aether-app associates the upload with a project before publishing.
 * Legacy messages may omit projectId (null).
 */
public record EstimateProcessingEvent(
        @JsonProperty("recordId") String recordId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty(value = "projectId", required = false) String projectId,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("gcsPath") String gcsPath,
        @JsonProperty("uploadedAt") String uploadedAt,
        @JsonProperty("uploadedBy") String uploadedBy
) {
}
