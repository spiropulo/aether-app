package com.aether.app.item;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class ItemGraphqlController {

    private final ItemService itemService;

    public ItemGraphqlController(ItemService itemService) {
        this.itemService = itemService;
    }

    @QueryMapping
    public Mono<PagedResult<Item>> items(@Argument String tenantId,
                                         @Argument String projectId,
                                         @Argument String taskId,
                                         @Argument PageInput page) {
        return itemService.getItems(tenantId, projectId, taskId, page);
    }

    @QueryMapping
    public Mono<Item> item(@Argument String id,
                           @Argument String tenantId,
                           @Argument String projectId,
                           @Argument String taskId) {
        return itemService.getItem(id, tenantId, projectId, taskId);
    }

    @MutationMapping
    public Mono<Item> createItem(@Argument CreateItemInput input) {
        return itemService.createItem(input);
    }

    @MutationMapping
    public Mono<Item> updateItem(@Argument String id,
                                  @Argument String tenantId,
                                  @Argument String projectId,
                                  @Argument String taskId,
                                  @Argument UpdateItemInput input) {
        return itemService.updateItem(id, tenantId, projectId, taskId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteItem(@Argument String id,
                                     @Argument String tenantId,
                                     @Argument String projectId,
                                     @Argument String taskId) {
        return itemService.deleteItem(id, tenantId, projectId, taskId);
    }
}
