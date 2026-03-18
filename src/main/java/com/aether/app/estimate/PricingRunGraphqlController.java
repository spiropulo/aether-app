package com.aether.app.estimate;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class PricingRunGraphqlController {

    private final PricingRunService pricingRunService;

    public PricingRunGraphqlController(PricingRunService pricingRunService) {
        this.pricingRunService = pricingRunService;
    }

    @QueryMapping
    public Flux<PricingRun> projectPricingRuns(@Argument String projectId, @Argument String tenantId) {
        return pricingRunService.listByProject(projectId, tenantId)
                .map(PricingRun::from);
    }

    @MutationMapping
    public Mono<Boolean> deletePricingRun(@Argument String id, @Argument String projectId, @Argument String tenantId) {
        return pricingRunService.delete(id, projectId, tenantId);
    }
}
