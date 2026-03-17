package com.aether.app.trainingdata;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrainingDataService {

    private static final TypeReference<List<Map<String, String>>> ENTRIES_TYPE = new TypeReference<>() {};

    private final TrainingDataRepository trainingDataRepository;
    private final ObjectMapper objectMapper;

    public TrainingDataService(TrainingDataRepository trainingDataRepository, ObjectMapper objectMapper) {
        this.trainingDataRepository = trainingDataRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse content JSON to list of key-value entries. Backward compat: if not valid JSON array, treat as legacy single entry.
     */
    public List<TrainingDataEntry> parseEntries(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> raw = objectMapper.readValue(content, ENTRIES_TYPE);
            if (raw == null) return List.of();
            List<TrainingDataEntry> result = new ArrayList<>();
            for (Map<String, String> m : raw) {
                String k = m != null ? m.get("key") : null;
                String v = m != null ? m.get("value") : null;
                if (k != null) result.add(new TrainingDataEntry(k, v != null ? v : ""));
            }
            return result;
        } catch (Exception e) {
            return List.of(new TrainingDataEntry("content", content));
        }
    }

    private String serializeEntries(List<TrainingDataEntryInput> entries) {
        if (entries == null || entries.isEmpty()) return "[]";
        try {
            List<Map<String, String>> raw = entries.stream()
                    .map(e -> Map.<String, String>of("key", e.getKey() != null ? e.getKey() : "", "value", e.getValue() != null ? e.getValue() : ""))
                    .toList();
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return "[]";
        }
    }

    public Mono<PagedResult<TrainingData>> getTenantTrainingData(String tenantId, PageInput page, String search) {
        return paginate(
                trainingDataRepository.findAllByTenantId(tenantId)
                        .filter(td -> td.getProjectId() == null)
                        .filter(td -> matchesSearch(td, search)),
                page
        );
    }

    public Mono<PagedResult<TrainingData>> getProjectTrainingData(String tenantId, String projectId,
                                                                   PageInput page, String search) {
        return paginate(
                trainingDataRepository.findAllByTenantId(tenantId)
                        .filter(td -> projectId.equals(td.getProjectId()))
                        .filter(td -> matchesSearch(td, search)),
                page
        );
    }

    public Mono<TrainingData> getTrainingDataEntry(String id, String tenantId) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId);
    }

    public Mono<TrainingData> createTenantTrainingData(CreateTenantTrainingDataInput input) {
        TrainingData entry = new TrainingData();
        entry.setTenantId(input.getTenantId());
        entry.setContent(serializeEntries(input.getEntries()));
        entry.setDescription(input.getDescription());
        Instant now = Instant.now();
        entry.setUploadedAt(now);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        return trainingDataRepository.save(entry);
    }

    public Mono<TrainingData> createProjectTrainingData(CreateProjectTrainingDataInput input) {
        TrainingData entry = new TrainingData();
        entry.setTenantId(input.getTenantId());
        entry.setProjectId(input.getProjectId());
        entry.setContent(serializeEntries(input.getEntries()));
        entry.setDescription(input.getDescription());
        Instant now = Instant.now();
        entry.setUploadedAt(now);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        return trainingDataRepository.save(entry);
    }

    public Mono<TrainingData> updateTrainingData(String id, String tenantId, UpdateTrainingDataInput input) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> {
                    if (input.getEntries() != null) {
                        existing.setContent(serializeEntries(input.getEntries()));
                    }
                    if (input.getDescription() != null) {
                        existing.setDescription(input.getDescription());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return trainingDataRepository.save(existing);
                });
    }

    public Mono<Boolean> deleteTrainingData(String id, String tenantId) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> trainingDataRepository.delete(existing).thenReturn(true));
    }

    private boolean matchesSearch(TrainingData td, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.toLowerCase();
        boolean contentMatch = td.getContent() != null && td.getContent().toLowerCase().contains(term);
        boolean descMatch = td.getDescription() != null && td.getDescription().toLowerCase().contains(term);
        boolean entriesMatch = parseEntries(td.getContent()).stream()
                .anyMatch(e -> (e.key() != null && e.key().toLowerCase().contains(term))
                        || (e.value() != null && e.value().toLowerCase().contains(term)));
        return contentMatch || descMatch || entriesMatch;
    }

    private Mono<PagedResult<TrainingData>> paginate(Flux<TrainingData> source, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return source.collectList()
                .map(all -> {
                    int total = all.size();
                    List<TrainingData> slice = all.stream()
                            .skip(offset)
                            .limit(limit)
                            .collect(Collectors.toList());
                    return new PagedResult<>(slice, total, limit, offset);
                });
    }
}
