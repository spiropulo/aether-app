package com.aether.app.estimate;

import com.aether.app.pretrain.PretrainData;
import com.aether.app.pretrain.PretrainService;
import com.aether.app.trainingdata.TrainingData;
import com.aether.app.trainingdata.TrainingDataRepository;
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

    private final PretrainService pretrainService;
    private final TrainingDataRepository trainingDataRepository;
    private final ObjectMapper objectMapper;

    public TrainingContextService(PretrainService pretrainService,
                                   TrainingDataRepository trainingDataRepository,
                                   ObjectMapper objectMapper) {
        this.pretrainService = pretrainService;
        this.trainingDataRepository = trainingDataRepository;
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
     * Check if the tenant has any training data (catalog or custom).
     */
    public Mono<Boolean> hasTrainingData(String tenantId) {
        Mono<Boolean> hasCatalog = pretrainService.getTenantSelectedPretrainData(tenantId)
                .hasElements();
        Mono<Boolean> hasCustom = trainingDataRepository.findAllByTenantId(tenantId)
                .filter(td -> td.getProjectId() == null)
                .hasElements();
        return Mono.zip(hasCatalog, hasCustom)
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    private Mono<TrainingContextDto> buildTrainingContext(String tenantId) {
        Flux<TrainingContextEntry> catalogFlux = pretrainService.getTenantSelectedPretrainData(tenantId)
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

    private TrainingContextEntry toCatalogEntry(PretrainData pd) {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrainingContextEntry(
            String source,
            String id,
            String title,
            String content,
            String fileName
    ) {}
}
