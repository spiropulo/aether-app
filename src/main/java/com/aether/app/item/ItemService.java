package com.aether.app.item;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public Mono<PagedResult<Item>> getItems(String tenantId, String projectId, String taskId, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return itemRepository.findAllByTaskIdAndProjectIdAndTenantId(taskId, projectId, tenantId)
                .collectList()
                .map(all -> {
                    int total = all.size();
                    var items = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
                    return new PagedResult<>(items, total, limit, offset);
                });
    }

    public Mono<Item> getItem(String id, String tenantId, String projectId, String taskId) {
        return itemRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId);
    }

    public Mono<Item> createItem(CreateItemInput input) {
        Item item = new Item();
        item.setTenantId(input.getTenantId());
        item.setProjectId(input.getProjectId());
        item.setTaskId(input.getTaskId());
        item.setName(input.getName());
        item.setDescription(input.getDescription());
        item.setQuantity(input.getQuantity());
        item.setCost(input.getCost());
        item.setTotal(computeTotal(input.getQuantity(), input.getCost()));
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return itemRepository.save(item);
    }

    public Mono<Item> updateItem(String id, String tenantId, String projectId, String taskId, UpdateItemInput input) {
        return itemRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> {
                    if (input.getName() != null) {
                        existing.setName(input.getName());
                    }
                    if (input.getDescription() != null) {
                        existing.setDescription(input.getDescription());
                    }
                    if (input.getQuantity() != null) {
                        existing.setQuantity(input.getQuantity());
                    }
                    if (input.getCost() != null) {
                        existing.setCost(input.getCost());
                    }
                    existing.setTotal(computeTotal(existing.getQuantity(), existing.getCost()));
                    existing.setUpdatedAt(Instant.now());
                    return itemRepository.save(existing);
                });
    }

    private Double computeTotal(Integer quantity, Double cost) {
        if (quantity == null || cost == null) {
            return null;
        }
        return quantity * cost;
    }

    public Mono<Boolean> deleteItem(String id, String tenantId, String projectId, String taskId) {
        return itemRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> itemRepository.delete(existing).thenReturn(true));
    }
}
