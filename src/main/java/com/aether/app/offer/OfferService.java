package com.aether.app.offer;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class OfferService {

    private final OfferRepository offerRepository;

    public OfferService(OfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    public Mono<PagedResult<Offer>> getOffers(String tenantId, String projectId, String taskId, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return offerRepository.findAllByTaskIdAndProjectIdAndTenantId(taskId, projectId, tenantId)
                .collectList()
                .map(all -> {
                    int total = all.size();
                    var items = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
                    return new PagedResult<>(items, total, limit, offset);
                });
    }

    public Mono<Offer> getOffer(String id, String tenantId, String projectId, String taskId) {
        return offerRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId);
    }

    public Mono<java.util.List<Offer>> getOffersByProject(String tenantId, String projectId) {
        return offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList();
    }

    public Mono<Offer> createOffer(CreateOfferInput input) {
        Offer offer = new Offer();
        offer.setTenantId(input.getTenantId());
        offer.setProjectId(input.getProjectId());
        offer.setTaskId(input.getTaskId());
        offer.setName(input.getName());
        offer.setDescription(input.getDescription());
        offer.setUom(input.getUom());
        offer.setQuantity(input.getQuantity());
        offer.setUnitCost(input.getUnitCost());
        offer.setDuration(input.getDuration());
        offer.setAssigneeIds(input.getAssigneeIds());
        offer.setTotal(computeTotal(input.getQuantity(), input.getUnitCost()));
        Instant now = Instant.now();
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);

        return offerRepository.save(offer);
    }

    public Mono<Offer> updateOffer(String id, String tenantId, String projectId, String taskId, UpdateOfferInput input) {
        return offerRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> {
                    if (input.getName() != null) {
                        existing.setName(input.getName());
                    }
                    if (input.getDescription() != null) {
                        existing.setDescription(input.getDescription());
                    }
                    if (input.getUom() != null) {
                        existing.setUom(input.getUom());
                    }
                    if (input.getQuantity() != null) {
                        existing.setQuantity(input.getQuantity());
                    }
                    if (input.getUnitCost() != null) {
                        existing.setUnitCost(input.getUnitCost());
                    }
                    if (input.getDuration() != null) {
                        existing.setDuration(input.getDuration());
                    }
                    if (input.getAssigneeIds() != null) {
                        existing.setAssigneeIds(input.getAssigneeIds());
                    }
                    existing.setTotal(computeTotal(existing.getQuantity(), existing.getUnitCost()));
                    existing.setUpdatedAt(Instant.now());
                    return offerRepository.save(existing);
                });
    }

    private Double computeTotal(Double quantity, Double unitCost) {
        if (quantity == null || unitCost == null) {
            return null;
        }
        return quantity * unitCost;
    }

    public Mono<Boolean> deleteOffer(String id, String tenantId, String projectId, String taskId) {
        return offerRepository.findByIdAndTaskIdAndProjectIdAndTenantId(id, taskId, projectId, tenantId)
                .flatMap(existing -> offerRepository.delete(existing).thenReturn(true));
    }
}
