package com.aether.app.pretrain;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PretrainedService {

    private final PretrainedDataRepository pretrainedDataRepository;
    private final PretrainSelectionRepository pretrainSelectionRepository;

    public PretrainedService(PretrainedDataRepository pretrainedDataRepository,
                             PretrainSelectionRepository pretrainSelectionRepository) {
        this.pretrainedDataRepository = pretrainedDataRepository;
        this.pretrainSelectionRepository = pretrainSelectionRepository;
    }

    // ─── Catalog (UI) ────────────────────────────────────────────────────────

    public Mono<PagedResult<PretrainedData>> getCatalog(PageInput page, String search) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return pretrainedDataRepository.findAll()
                .filter(entry -> matchesSearch(entry, search))
                .collectList()
                .map(all -> {
                    int total = all.size();
                    List<PretrainedData> slice = all.stream()
                            .skip(offset)
                            .limit(limit)
                            .collect(Collectors.toList());
                    return new PagedResult<>(slice, total, limit, offset);
                });
    }

    public Mono<PretrainedData> getCatalogEntry(String id) {
        return pretrainedDataRepository.findById(id);
    }

    // ─── Tenant selections (UI mutations) ────────────────────────────────────

    /**
     * Associates a catalog entry with a tenant at the tenant level (no taskId).
     * Idempotent: returns the existing selection if already present.
     *
     * NOTE: Firestore derived queries cannot filter on null, so we fetch all
     * selections for this tenant+pretrainData pair and filter taskId == null
     * in-memory to avoid the "Map value cannot be null" error.
     */
    public Mono<PretrainSelection> selectForTenant(String tenantId, String pretrainDataId) {
        return pretrainSelectionRepository
                .findAllByTenantIdAndPretrainDataId(tenantId, pretrainDataId)
                .filter(sel -> sel.getTaskId() == null)
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    PretrainSelection sel = new PretrainSelection();
                    sel.setTenantId(tenantId);
                    sel.setPretrainDataId(pretrainDataId);
                    sel.setCreatedAt(Instant.now());
                    return pretrainSelectionRepository.save(sel);
                }));
    }

    /**
     * Associates a catalog entry with a specific task under a tenant.
     * Idempotent: returns the existing selection if already present.
     */
    public Mono<PretrainSelection> selectForTask(String tenantId, String taskId, String pretrainDataId) {
        return pretrainSelectionRepository
                .findByTenantIdAndPretrainDataIdAndTaskId(tenantId, pretrainDataId, taskId)
                .switchIfEmpty(Mono.defer(() -> {
                    PretrainSelection sel = new PretrainSelection();
                    sel.setTenantId(tenantId);
                    sel.setTaskId(taskId);
                    sel.setPretrainDataId(pretrainDataId);
                    sel.setCreatedAt(Instant.now());
                    return pretrainSelectionRepository.save(sel);
                }));
    }

    public Mono<Boolean> deselect(String id, String tenantId) {
        return pretrainSelectionRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(sel -> pretrainSelectionRepository.delete(sel).thenReturn(true));
    }

    // ─── Agent queries ────────────────────────────────────────────────────────

    /**
     * Returns the full PretrainedData content for all catalog entries the tenant has
     * selected (tenant-level selections only — no taskId filter).
     * Used by the agent to load the tenant's default pre-training context.
     */
    public Flux<PretrainedData> getTenantSelectedPretrainData(String tenantId) {
        return pretrainSelectionRepository.findAllByTenantId(tenantId)
                .filter(sel -> sel.getTaskId() == null)
                .flatMap(sel -> pretrainedDataRepository.findById(sel.getPretrainDataId()));
    }

    /**
     * Returns the full PretrainedData content for all catalog entries selected for a
     * specific task under a tenant. Used by the agent to load task-specific training.
     */
    public Flux<PretrainedData> getTaskSelectedPretrainData(String tenantId, String taskId) {
        return pretrainSelectionRepository.findAllByTenantIdAndTaskId(tenantId, taskId)
                .flatMap(sel -> pretrainedDataRepository.findById(sel.getPretrainDataId()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean matchesSearch(PretrainedData entry, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.toLowerCase();
        boolean titleMatch = entry.getTitle() != null && entry.getTitle().toLowerCase().contains(term);
        boolean contentMatch = entry.getTrainingContent() != null
                && entry.getTrainingContent().toLowerCase().contains(term);
        return titleMatch || contentMatch;
    }
}
