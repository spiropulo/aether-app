package com.aether.app.estimate;

import com.aether.app.trainingdata.PricingFact;
import com.aether.app.trainingdata.TrainingData;
import com.aether.app.trainingdata.TrainingDataEntry;
import com.aether.app.trainingdata.TrainingDataRepository;
import com.aether.app.trainingdata.TrainingDataService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Aggregates tenant training from custom {@link TrainingData} only (global + project scope).
 * Used when invoking the tenant-adaptive agent.
 */
@Service
public class TrainingContextService {

    private static final Logger log = LoggerFactory.getLogger(TrainingContextService.class);

    private final TrainingDataRepository trainingDataRepository;
    private final TrainingDataService trainingDataService;
    private final ObjectMapper objectMapper;

    public TrainingContextService(TrainingDataRepository trainingDataRepository,
                                   TrainingDataService trainingDataService,
                                   ObjectMapper objectMapper) {
        this.trainingDataRepository = trainingDataRepository;
        this.trainingDataService = trainingDataService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch all tenant-scope custom training and serialize to JSON for the AI agent.
     */
    public Mono<String> getTrainingContextJson(String tenantId) {
        return buildTrainingContext(tenantId)
                .flatMap(ctx -> {
                    if (ctx.customEntries().isEmpty()) {
                        return Mono.just("");
                    }
                    try {
                        return Mono.just(objectMapper.writeValueAsString(ctx));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize training context for tenant {}: {}", tenantId, e.getMessage());
                        return Mono.just("");
                    }
                });
    }

    /**
     * Build run context for UI display (PDF upload runs).
     */
    public Mono<String> getRunContextForDisplay(String tenantId) {
        return buildTrainingContext(tenantId)
                .flatMap(ctx -> {
                    if (ctx.customEntries().isEmpty()) {
                        return Mono.just("{\"customEntries\":[],\"hasTraining\":false}");
                    }
                    try {
                        RunContextDisplay display = new RunContextDisplay(
                                ctx.customEntries().stream()
                                        .map(e -> new RunContextEntryDisplay(e.source(), e.id(), e.title(), truncate(e.content(), 200)))
                                        .toList(),
                                true
                        );
                        return Mono.just(objectMapper.writeValueAsString(display));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize run context for tenant {}: {}", tenantId, e.getMessage());
                        return Mono.just("{\"customEntries\":[],\"hasTraining\":false}");
                    }
                });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunContextDisplay(
            List<RunContextEntryDisplay> customEntries,
            boolean hasTraining
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunContextEntryDisplay(String source, String id, String title, String contentPreview) {}

    /**
     * Training for a project: project-level + tenant-level custom data + merged structured facts.
     */
    public Mono<String> getTrainingContextForProject(String tenantId, String projectId) {
        return buildTrainingContextForProject(tenantId, projectId)
                .flatMap(ctx -> {
                    if (ctx.tenantCustomEntries().isEmpty() && ctx.projectCustomEntries().isEmpty()
                            && ctx.structuredPricingFacts().isEmpty()) {
                        return Mono.just("");
                    }
                    try {
                        return Mono.just(objectMapper.writeValueAsString(ctx));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize training context for tenant {} project {}: {}", tenantId, projectId, e.getMessage());
                        return Mono.just("");
                    }
                });
    }

    /**
     * True if the tenant has any tenant-scope custom training rows.
     */
    public Mono<Boolean> hasTrainingData(String tenantId) {
        return trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .hasElements();
    }

    private Mono<TrainingContextDto> buildTrainingContext(String tenantId) {
        return trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .map(this::toCustomEntry)
                .collectList()
                .map(TrainingContextDto::new);
    }

    private Mono<ProjectTrainingContextDto> buildTrainingContextForProject(String tenantId, String projectId) {
        Mono<List<TrainingDataEntry>> tenantCustomPairs = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .flatMapIterable(td -> trainingDataService.parseEntries(td.getContent()))
                .collectList();

        Mono<List<TrainingDataEntry>> projectCustomPairs = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> projectId.equals(td.getProjectId()))
                .flatMapIterable(td -> trainingDataService.parseEntries(td.getContent()))
                .collectList();

        Mono<List<PricingFact>> structuredFacts = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null || projectId.equals(td.getProjectId()))
                .flatMapIterable(td -> trainingDataService.parsePricingFacts(td.getContent()))
                .collectList();

        return Mono.zip(projectCustomPairs, tenantCustomPairs, structuredFacts)
                .map(tuple -> new ProjectTrainingContextDto(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

    private TrainingContextEntry toCustomEntry(TrainingData td) {
        return new TrainingContextEntry(
                "custom",
                td.getId(),
                td.getDescription(),
                td.getContent(),
                null
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrainingContextDto(
            List<TrainingContextEntry> customEntries
    ) {}

    /**
     * Order for agent: (1) project training, (2) tenant global training, (3) structured facts.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectTrainingContextDto(
            List<TrainingDataEntry> projectCustomEntries,
            List<TrainingDataEntry> tenantCustomEntries,
            List<PricingFact> structuredPricingFacts
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrainingContextEntry(
            String source,
            String id,
            String title,
            String content,
            String fileName
    ) {}
}
