package com.aether.app.estimate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "aether.pubsub.estimate-subscription")
public class EstimatePubSubListener {

    private static final Logger log = LoggerFactory.getLogger(EstimatePubSubListener.class);

    private final PubSubTemplate pubSubTemplate;
    private final String subscriptionId;
    private final StorageService storageService;
    private final EstimateAgentClient agentClient;
    private final EstimateService estimateService;
    private final TrainingContextService trainingContextService;
    private final ObjectMapper objectMapper;

    public EstimatePubSubListener(PubSubTemplate pubSubTemplate,
                                  @Value("${aether.pubsub.estimate-subscription}") String subscriptionId,
                                  StorageService storageService,
                                  EstimateAgentClient agentClient,
                                  EstimateService estimateService,
                                  TrainingContextService trainingContextService,
                                  ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.subscriptionId = subscriptionId;
        this.storageService = storageService;
        this.agentClient = agentClient;
        this.estimateService = estimateService;
        this.trainingContextService = trainingContextService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        if (!agentClient.isConfigured()) {
            log.warn("aether.agent.project-pdf-sync-url is not configured — skipping Pub/Sub subscription for estimate processing");
            return;
        }
        log.info("Subscribing to Pub/Sub subscription {} for estimate processing", subscriptionId);
        pubSubTemplate.subscribe(subscriptionId, this::handleMessage);
    }

    private void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        String payload = message.getPubsubMessage().getData().toStringUtf8();
        try {
            EstimateProcessingEvent event = objectMapper.readValue(payload, EstimateProcessingEvent.class);
            try {
                processEvent(event)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block(Duration.ofMinutes(10));
                message.ack();
            } catch (Exception e) {
                log.error("Failed to process estimate event {}: {}", event.recordId(), e.getMessage());
                message.nack();
            }
        } catch (Exception e) {
            log.error("Failed to parse Pub/Sub message: {}", e.getMessage());
            message.nack();
        }
    }

    private Mono<Void> processEvent(EstimateProcessingEvent event) {
        String projectId = event.projectId() != null && !event.projectId().isBlank() ? event.projectId() : null;
        log.info("Processing estimate event: recordId={}, projectId={}, gcsPath={}", event.recordId(), projectId, event.gcsPath());

        if (projectId == null) {
            log.error("Estimate event {} has no projectId. Skipping.", event.recordId());
            return estimateService.updateStatus(event.recordId(), event.tenantId(), UploadStatus.FAILED).then();
        }

        return estimateService.updateStatus(event.recordId(), event.tenantId(), UploadStatus.PROCESSING)
                .then(trainingContextService.getRunContextForDisplay(event.tenantId())
                        .flatMap(runContext -> estimateService.updateRunContext(event.recordId(), event.tenantId(), runContext)))
                .then(storageService.download(event.gcsPath()))
                .flatMap(pdfBytes -> {
                    if (!agentClient.isConfigured()) {
                        log.error("Project PDF sync agent URL not configured; record {}", event.recordId());
                        return Mono.error(new IllegalStateException(
                                "Project PDF sync agent URL not configured. Set aether.agent.project-pdf-sync-url "
                                        + "(e.g. http://localhost:8055/api/v1/project-pdf-sync/process) or env AETHER_AGENT_PROJECT_PDF_SYNC_URL."));
                    }
                    return agentClient.processProjectPdfSync(pdfBytes, event.fileName(), event.tenantId(), projectId);
                })
                .flatMap(agentResponseJson -> {
                    String agentActivityLog = extractAgentActivityLog(agentResponseJson);
                    return estimateService.updateAgentActivityLog(event.recordId(), event.tenantId(), agentActivityLog)
                            .then(estimateService.updateStatus(event.recordId(), event.tenantId(), UploadStatus.COMPLETED))
                            .then();
                })
                .onErrorResume(ex -> {
                    log.error("Estimate processing failed for record {}: {}", event.recordId(), ex.getMessage());
                    return estimateService.updateStatus(event.recordId(), event.tenantId(), UploadStatus.FAILED)
                            .then();
                });
    }

    private String extractAgentActivityLog(String agentResponseJson) {
        if (agentResponseJson == null || agentResponseJson.isBlank()) {
            return "[]";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(agentResponseJson);
            com.fasterxml.jackson.databind.JsonNode logNode = root.get("agent_activity_log");
            if (logNode != null && logNode.isArray()) {
                return objectMapper.writeValueAsString(logNode);
            }
        } catch (Exception e) {
            log.warn("Could not extract agent_activity_log from response: {}", e.getMessage());
        }
        return "[]";
    }

}
