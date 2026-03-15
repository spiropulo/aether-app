package com.aether.app.pretrain;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class PretrainGraphqlController {

    private final PretrainService pretrainService;

    public PretrainGraphqlController(PretrainService pretrainService) {
        this.pretrainService = pretrainService;
    }

    // ─── Catalog (UI) ────────────────────────────────────────────────────────

    @QueryMapping
    public Mono<PagedResult<PretrainData>> pretrainCatalog(@Argument PageInput page,
                                                            @Argument String search) {
        return pretrainService.getCatalog(page, search);
    }

    @QueryMapping
    public Mono<PretrainData> pretrainEntry(@Argument String id) {
        return pretrainService.getCatalogEntry(id);
    }

    // ─── Agent queries ────────────────────────────────────────────────────────

    @QueryMapping
    public Flux<PretrainData> tenantSelectedPretrainData(@Argument String tenantId) {
        return pretrainService.getTenantSelectedPretrainData(tenantId);
    }

    @QueryMapping
    public Flux<PretrainData> taskSelectedPretrainData(@Argument String tenantId,
                                                        @Argument String taskId) {
        return pretrainService.getTaskSelectedPretrainData(tenantId, taskId);
    }

    // ─── Selection mutations (UI) ─────────────────────────────────────────────

    @MutationMapping
    public Mono<PretrainSelection> selectPretrainData(@Argument String tenantId,
                                                       @Argument String pretrainDataId) {
        return pretrainService.selectForTenant(tenantId, pretrainDataId);
    }

    @MutationMapping
    public Mono<PretrainSelection> selectPretrainDataForTask(@Argument String tenantId,
                                                              @Argument String taskId,
                                                              @Argument String pretrainDataId) {
        return pretrainService.selectForTask(tenantId, taskId, pretrainDataId);
    }

    @MutationMapping
    public Mono<Boolean> deselectPretrainData(@Argument String id, @Argument String tenantId) {
        return pretrainService.deselect(id, tenantId);
    }
}
