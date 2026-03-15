package com.aether.app.tenant;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class TenantGraphqlController {

    private final TenantService tenantService;

    public TenantGraphqlController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @QueryMapping
    public Flux<Tenant> tenants(@Argument String tenantId) {
        return tenantService.getTenantsForTenant(tenantId);
    }

    @QueryMapping
    public Mono<Tenant> tenant(@Argument String id, @Argument String tenantId) {
        return tenantService.getTenant(id, tenantId);
    }

    @MutationMapping
    public Mono<Tenant> createTenant(@Argument CreateTenantInput input) {
        return tenantService.createTenant(input);
    }

    @MutationMapping
    public Mono<Tenant> updateTenant(@Argument String id,
                                     @Argument String tenantId,
                                     @Argument UpdateTenantInput input) {
        return tenantService.updateTenant(id, tenantId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteTenant(@Argument String id, @Argument String tenantId) {
        return tenantService.deleteTenant(id, tenantId);
    }
}
