package com.aether.app.trainingdata;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrainingDataService {

    private final TrainingDataRepository trainingDataRepository;

    public TrainingDataService(TrainingDataRepository trainingDataRepository) {
        this.trainingDataRepository = trainingDataRepository;
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
        entry.setContent(input.getContent());
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
        entry.setContent(input.getContent());
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
                    if (input.getContent() != null) {
                        existing.setContent(input.getContent());
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
        return contentMatch || descMatch;
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
