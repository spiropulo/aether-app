package com.aether.app.estimate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Health and configuration status of the estimate upload service.")
public class EstimateHealthResponse {

    @Schema(description = "Always \"ok\" when the service is ready.", example = "ok")
    private final String status;

    @Schema(description = "GCS bucket where uploaded PDFs are stored.", example = "aether-estimates")
    private final String gcsBucket;

    @Schema(description = "Pub/Sub topic used to trigger AI processing. Empty string if not configured.", example = "estimate-processing")
    private final String pubsubTopic;

    public EstimateHealthResponse(String status, String gcsBucket, String pubsubTopic) {
        this.status = status;
        this.gcsBucket = gcsBucket;
        this.pubsubTopic = pubsubTopic;
    }

    public String getStatus() {
        return status;
    }

    public String getGcsBucket() {
        return gcsBucket;
    }

    public String getPubsubTopic() {
        return pubsubTopic;
    }
}
