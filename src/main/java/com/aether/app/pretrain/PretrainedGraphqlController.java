package com.aether.app.pretrain;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL controller for pretrained data catalog and selections.
 * Exposes pretrainCatalog, pretrainEntry, etc. for UI compatibility (schema type PretrainData).
 */
@Controller
public class PretrainedGraphqlController {

    private final PretrainedService pretrainedService;

    public PretrainedGraphqlController(PretrainedService pretrainedService) {
        this.pretrainedService = pretrainedService;
    }

    // ─── Catalog (UI) ────────────────────────────────────────────────────────

    @QueryMapping
    public Mono<PagedResult<PretrainedData>> pretrainCatalog(@Argument PageInput page,
                                                             @Argument String search) {
        return pretrainedService.getCatalog(page, search);
    }

    @QueryMapping
    public Mono<PretrainedData> pretrainEntry(@Argument String id) {
        return pretrainedService.getCatalogEntry(id);
    }

    // ─── Agent queries ────────────────────────────────────────────────────────

    @QueryMapping
    public Flux<PretrainedData> tenantSelectedPretrainData(@Argument String tenantId) {
        return pretrainedService.getTenantSelectedPretrainData(tenantId);
    }

    @QueryMapping
    public Flux<PretrainedData> taskSelectedPretrainData(@Argument String tenantId,
                                                         @Argument String taskId) {
        return pretrainedService.getTaskSelectedPretrainData(tenantId, taskId);
    }

    // ─── Selection mutations (UI) ─────────────────────────────────────────────

    @MutationMapping
    public Mono<PretrainSelection> selectPretrainData(@Argument String tenantId,
                                                       @Argument String pretrainDataId) {
        return pretrainedService.selectForTenant(tenantId, pretrainDataId);
    }

    @MutationMapping
    public Mono<PretrainSelection> selectPretrainDataForTask(@Argument String tenantId,
                                                             @Argument String taskId,
                                                             @Argument String pretrainDataId) {
        return pretrainedService.selectForTask(tenantId, taskId, pretrainDataId);
    }

    @MutationMapping
    public Mono<Boolean> deselectPretrainData(@Argument String id, @Argument String tenantId) {
        return pretrainedService.deselect(id, tenantId);
    }
}
