package com.aether.app.offer;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class OfferGraphqlController {

    private final OfferService offerService;

    public OfferGraphqlController(OfferService offerService) {
        this.offerService = offerService;
    }

    @QueryMapping
    public Mono<PagedResult<Offer>> offers(@Argument String tenantId,
                                           @Argument String projectId,
                                           @Argument String taskId,
                                           @Argument PageInput page) {
        return offerService.getOffers(tenantId, projectId, taskId, page);
    }

    @QueryMapping
    public Mono<java.util.List<Offer>> offersByProject(@Argument String tenantId,
                                                        @Argument String projectId) {
        return offerService.getOffersByProject(tenantId, projectId);
    }

    @QueryMapping
    public Mono<Offer> offer(@Argument String id,
                             @Argument String tenantId,
                             @Argument String projectId,
                             @Argument String taskId) {
        return offerService.getOffer(id, tenantId, projectId, taskId);
    }

    @MutationMapping
    public Mono<Offer> createOffer(@Argument CreateOfferInput input) {
        return offerService.createOffer(input);
    }

    @MutationMapping
    public Mono<Offer> updateOffer(@Argument String id,
                                   @Argument String tenantId,
                                   @Argument String projectId,
                                   @Argument String taskId,
                                   @Argument UpdateOfferInput input,
                                   @ContextValue(name = "authRole", required = false) String authRole) {
        if (input.getWorkCompleted() != null && !"ADMIN".equals(authRole)) {
            return Mono.error(new IllegalArgumentException("Only admins can update offer work completion"));
        }
        return offerService.updateOffer(id, tenantId, projectId, taskId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteOffer(@Argument String id,
                                      @Argument String tenantId,
                                      @Argument String projectId,
                                      @Argument String taskId) {
        return offerService.deleteOffer(id, tenantId, projectId, taskId);
    }
}
