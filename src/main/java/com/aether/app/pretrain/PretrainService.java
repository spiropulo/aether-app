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
public class PretrainService {

    private final PretrainDataRepository pretrainDataRepository;
    private final PretrainSelectionRepository pretrainSelectionRepository;

    public PretrainService(PretrainDataRepository pretrainDataRepository,
                           PretrainSelectionRepository pretrainSelectionRepository) {
        this.pretrainDataRepository = pretrainDataRepository;
        this.pretrainSelectionRepository = pretrainSelectionRepository;
    }

    // ─── Catalog (UI) ────────────────────────────────────────────────────────

    public Mono<PagedResult<PretrainData>> getCatalog(PageInput page, String search) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return pretrainDataRepository.findAll()
                .filter(entry -> matchesSearch(entry, search))
                .collectList()
                .map(all -> {
                    int total = all.size();
                    List<PretrainData> slice = all.stream()
                            .skip(offset)
                            .limit(limit)
                            .collect(Collectors.toList());
                    return new PagedResult<>(slice, total, limit, offset);
                });
    }

    public Mono<PretrainData> getCatalogEntry(String id) {
        return pretrainDataRepository.findById(id);
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
     * Returns the full PretrainData content for all catalog entries the tenant has
     * selected (tenant-level selections only — no taskId filter).
     * Used by the agent to load the tenant's default pre-training context.
     */
    public Flux<PretrainData> getTenantSelectedPretrainData(String tenantId) {
        return pretrainSelectionRepository.findAllByTenantId(tenantId)
                .filter(sel -> sel.getTaskId() == null)
                .flatMap(sel -> pretrainDataRepository.findById(sel.getPretrainDataId()));
    }

    /**
     * Returns the full PretrainData content for all catalog entries selected for a
     * specific task under a tenant. Used by the agent to load task-specific training.
     */
    public Flux<PretrainData> getTaskSelectedPretrainData(String tenantId, String taskId) {
        return pretrainSelectionRepository.findAllByTenantIdAndTaskId(tenantId, taskId)
                .flatMap(sel -> pretrainDataRepository.findById(sel.getPretrainDataId()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean matchesSearch(PretrainData entry, String search) {
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
