package com.aether.app.estimate;

import com.aether.app.pretrain.PretrainedData;
import com.aether.app.pretrain.PretrainedService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates tenant-specific training data from:
 * - AI Catalog selections (PretrainData)
 * - Custom tenant training (TrainingData with projectId=null)
 * <p>
 * Used when invoking the AI agent so tenant-adaptive intelligence can be applied.
 */
@Service
public class TrainingContextService {

    private static final Logger log = LoggerFactory.getLogger(TrainingContextService.class);

    private final PretrainedService pretrainedService;
    private final TrainingDataRepository trainingDataRepository;
    private final TrainingDataService trainingDataService;
    private final ObjectMapper objectMapper;

    public TrainingContextService(PretrainedService pretrainedService,
                                   TrainingDataRepository trainingDataRepository,
                                   TrainingDataService trainingDataService,
                                   ObjectMapper objectMapper) {
        this.pretrainedService = pretrainedService;
        this.trainingDataRepository = trainingDataRepository;
        this.trainingDataService = trainingDataService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch all tenant training context (catalog + custom) and serialize to JSON
     * for passing to the AI agent. Returns empty JSON array if no training exists.
     */
    public Mono<String> getTrainingContextJson(String tenantId) {
        return buildTrainingContext(tenantId)
                .flatMap(ctx -> {
                    if (ctx.catalogEntries().isEmpty() && ctx.customEntries().isEmpty()) {
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
     * Build run context for UI display (training sources, metadata).
     * Stored in PdfUploadRecord so the UI can show what was used for each run.
     */
    public Mono<String> getRunContextForDisplay(String tenantId) {
        return buildTrainingContext(tenantId)
                .flatMap(ctx -> {
                    if (ctx.catalogEntries().isEmpty() && ctx.customEntries().isEmpty()) {
                        return Mono.just("{\"catalogEntries\":[],\"customEntries\":[],\"hasTraining\":false}");
                    }
                    try {
                        RunContextDisplay display = new RunContextDisplay(
                                ctx.catalogEntries().stream()
                                        .map(e -> new RunContextEntryDisplay(e.source(), e.id(), e.title(), truncate(e.content(), 200)))
                                        .toList(),
                                ctx.customEntries().stream()
                                        .map(e -> new RunContextEntryDisplay(e.source(), e.id(), e.title(), truncate(e.content(), 200)))
                                        .toList(),
                                true
                        );
                        return Mono.just(objectMapper.writeValueAsString(display));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize run context for tenant {}: {}", tenantId, e.getMessage());
                        return Mono.just("{\"catalogEntries\":[],\"customEntries\":[],\"hasTraining\":false}");
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
            List<RunContextEntryDisplay> catalogEntries,
            List<RunContextEntryDisplay> customEntries,
            boolean hasTraining
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunContextEntryDisplay(String source, String id, String title, String contentPreview) {}

    /**
     * Fetch training context for a specific project: tenant-level (catalog + custom with projectId=null)
     * plus project-level (custom with projectId set). Merged and serialized for the tenant-adaptive agent.
     * Returns empty string if no training exists.
     */
    public Mono<String> getTrainingContextForProject(String tenantId, String projectId) {
        return buildTrainingContextForProject(tenantId, projectId)
                .flatMap(ctx -> {
                    if (ctx.catalogEntries().isEmpty() && ctx.tenantCustomEntries().isEmpty() && ctx.projectCustomEntries().isEmpty()) {
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
     * Check if the tenant has any training data (catalog or custom).
     */
    public Mono<Boolean> hasTrainingData(String tenantId) {
        Mono<Boolean> hasCatalog = pretrainedService.getTenantSelectedPretrainData(tenantId)
                .hasElements();
        Mono<Boolean> hasCustom = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .hasElements();
        return Mono.zip(hasCatalog, hasCustom)
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    private Mono<TrainingContextDto> buildTrainingContext(String tenantId) {
        Flux<TrainingContextEntry> catalogFlux = pretrainedService.getTenantSelectedPretrainData(tenantId)
                .map(this::toCatalogEntry);

        Flux<TrainingContextEntry> customFlux = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .map(this::toCustomEntry);

        return Flux.merge(catalogFlux, customFlux)
                .collectList()
                .map(all -> {
                    List<TrainingContextEntry> catalog = new ArrayList<>();
                    List<TrainingContextEntry> custom = new ArrayList<>();
                    for (TrainingContextEntry e : all) {
                        if ("catalog".equals(e.source())) {
                            catalog.add(e);
                        } else {
                            custom.add(e);
                        }
                    }
                    return new TrainingContextDto(catalog, custom);
                });
    }

    private Mono<ProjectTrainingContextDto> buildTrainingContextForProject(String tenantId, String projectId) {
        Flux<TrainingContextEntry> catalogFlux = pretrainedService.getTenantSelectedPretrainData(tenantId)
                .map(this::toCatalogEntry);

        Mono<List<TrainingDataEntry>> tenantCustomPairs = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .flatMapIterable(td -> trainingDataService.parseEntries(td.getContent()))
                .collectList();

        Mono<List<TrainingDataEntry>> projectCustomPairs = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> projectId.equals(td.getProjectId()))
                .flatMapIterable(td -> trainingDataService.parseEntries(td.getContent()))
                .collectList();

        Mono<List<TrainingContextEntry>> catalogList = catalogFlux.collectList();

        return Mono.zip(catalogList, projectCustomPairs, tenantCustomPairs)
                .map(tuple -> new ProjectTrainingContextDto(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

    private TrainingContextEntry toCatalogEntry(PretrainedData pd) {
        return new TrainingContextEntry(
                "catalog",
                pd.getId(),
                pd.getTitle(),
                pd.getTrainingContent(),
                pd.getFileName()
        );
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
            List<TrainingContextEntry> catalogEntries,
            List<TrainingContextEntry> customEntries
    ) {}

    /**
     * Training data order for agent: (1) AI Training catalog, (2) Project Detail training, (3) AI Training custom.
     * Field order determines JSON serialization order sent to the agent.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectTrainingContextDto(
            List<TrainingContextEntry> catalogEntries,
            List<TrainingDataEntry> projectCustomEntries,
            List<TrainingDataEntry> tenantCustomEntries
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
