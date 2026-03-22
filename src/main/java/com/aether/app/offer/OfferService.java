package com.aether.app.offer;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    /** unitCost above this may indicate AI pricing error (e.g. total used as unitCost). */
    private static final double UNIT_COST_OUTLIER_THRESHOLD = 100_000.0;

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

    public Mono<Double> getProjectTotal(String tenantId, String projectId) {
        return offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .map(o -> o.getTotal() != null ? o.getTotal() : 0.0)
                .reduce(0.0, (a, b) -> a + b);
    }

    /**
     * Distinct project IDs in the tenant that have at least one offer listing {@code userProfileId} in {@code assigneeIds}.
     */
    public Mono<java.util.Set<String>> findDistinctProjectIdsForAssignee(String tenantId, String userProfileId) {
        return offerRepository.findAllByTenantIdAndAssigneeIdsContaining(tenantId, userProfileId)
                .map(Offer::getProjectId)
                .collectList()
                .map(HashSet::new);
    }

    public Mono<Boolean> isUserAssigneeOnProject(String tenantId, String projectId, String userProfileId) {
        return offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .any(offer -> {
                    List<String> ids = offer.getAssigneeIds();
                    return ids != null && ids.contains(userProfileId);
                });
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
                        double uc = input.getUnitCost();
                        if (uc < 0) {
                            return Mono.error(new IllegalArgumentException("unitCost cannot be negative"));
                        }
                        if (uc > UNIT_COST_OUTLIER_THRESHOLD) {
                            log.warn("Offer unitCost outlier: id={} name={} unitCost={} (threshold={}). Possible AI pricing error.",
                                    id, existing.getName(), uc, UNIT_COST_OUTLIER_THRESHOLD);
                        }
                        existing.setUnitCost(uc);
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
