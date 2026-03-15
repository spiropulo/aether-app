package com.aether.app.labor;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class LaborGraphqlController {

    private final LaborService laborService;

    public LaborGraphqlController(LaborService laborService) {
        this.laborService = laborService;
    }

    @QueryMapping
    public Mono<PagedResult<Labor>> labors(@Argument String tenantId,
                                           @Argument String projectId,
                                           @Argument String taskId,
                                           @Argument PageInput page) {
        return laborService.getLabors(tenantId, projectId, taskId, page);
    }

    @QueryMapping
    public Mono<Labor> labor(@Argument String id,
                             @Argument String tenantId,
                             @Argument String projectId,
                             @Argument String taskId) {
        return laborService.getLabor(id, tenantId, projectId, taskId);
    }

    @MutationMapping
    public Mono<Labor> createLabor(@Argument CreateLaborInput input) {
        return laborService.createLabor(input);
    }

    @MutationMapping
    public Mono<Labor> updateLabor(@Argument String id,
                                    @Argument String tenantId,
                                    @Argument String projectId,
                                    @Argument String taskId,
                                    @Argument UpdateLaborInput input) {
        return laborService.updateLabor(id, tenantId, projectId, taskId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteLabor(@Argument String id,
                                      @Argument String tenantId,
                                      @Argument String projectId,
                                      @Argument String taskId) {
        return laborService.deleteLabor(id, tenantId, projectId, taskId);
    }
}
