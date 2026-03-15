package com.aether.app.labor;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class LaborService {

    private final LaborRepository laborRepository;

    public LaborService(LaborRepository laborRepository) {
        this.laborRepository = laborRepository;
    }

    public Mono<PagedResult<Labor>> getLabors(String tenantId, String projectId, String taskId, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return laborRepository.findAllByTaskIdAndProjectIdAndTenantId(taskId, projectId, tenantId)
                .collectList()
                .map(all -> {
                    int total = all.size();
                    var items = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
                    return new PagedResult<>(items, total, limit, offset);
                });
    }

    public Mono<Labor> getLabor(String id, String tenantId, String projectId, String taskId) {
        return laborRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId);
    }

    public Mono<Labor> createLabor(CreateLaborInput input) {
        Labor labor = new Labor();
        labor.setTenantId(input.getTenantId());
        labor.setProjectId(input.getProjectId());
        labor.setTaskId(input.getTaskId());
        labor.setName(input.getName());
        labor.setDescription(input.getDescription());
        labor.setTime(input.getTime());
        labor.setCost(input.getCost());
        Instant now = Instant.now();
        labor.setCreatedAt(now);
        labor.setUpdatedAt(now);

        return laborRepository.save(labor);
    }

    public Mono<Labor> updateLabor(String id, String tenantId, String projectId, String taskId, UpdateLaborInput input) {
        return laborRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> {
                    if (input.getName() != null) {
                        existing.setName(input.getName());
                    }
                    if (input.getDescription() != null) {
                        existing.setDescription(input.getDescription());
                    }
                    if (input.getTime() != null) {
                        existing.setTime(input.getTime());
                    }
                    if (input.getCost() != null) {
                        existing.setCost(input.getCost());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return laborRepository.save(existing);
                });
    }

    public Mono<Boolean> deleteLabor(String id, String tenantId, String projectId, String taskId) {
        return laborRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> laborRepository.delete(existing).thenReturn(true));
    }
}
