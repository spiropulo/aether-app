package com.aether.app.estimate;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class PricingRunService {

    private final PricingRunRepository pricingRunRepository;

    public PricingRunService(PricingRunRepository pricingRunRepository) {
        this.pricingRunRepository = pricingRunRepository;
    }

    /**
     * Save a pricing run record after a successful agent run.
     * @param offersSnapshot JSON array of offer snapshots (name, quantity, unitCost, total, taskId, uom)
     * @param report Human-readable report (high-level summary and pricing decisions)
     */
    public Mono<PricingRunRecord> save(String tenantId, String projectId,
                                       String agentReport, String toolCallLog, String agentActivityLog,
                                       Integer toolCallsMade, String offersSnapshot, String report) {
        PricingRunRecord record = new PricingRunRecord();
        record.setTenantId(tenantId);
        record.setProjectId(projectId);
        record.setAgentReport(agentReport);
        record.setToolCallLog(toolCallLog);
        record.setAgentActivityLog(agentActivityLog);
        record.setToolCallsMade(toolCallsMade);
        record.setOffersSnapshot(offersSnapshot);
        record.setReport(report);
        record.setRunAt(Instant.now());
        return pricingRunRepository.save(record).thenReturn(record);
    }

    /**
     * List all pricing runs for a project, newest first.
     */
    public Flux<PricingRunRecord> listByProject(String projectId, String tenantId) {
        return pricingRunRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .sort((a, b) -> {
                    Instant ra = a.getRunAt();
                    Instant rb = b.getRunAt();
                    if (ra == null && rb == null) return 0;
                    if (ra == null) return 1;
                    if (rb == null) return -1;
                    return rb.compareTo(ra); // newest first
                });
    }

    /**
     * Delete a pricing run. Verifies projectId and tenantId match for access control.
     */
    public Mono<Boolean> delete(String id, String projectId, String tenantId) {
        return pricingRunRepository.findById(id)
                .filter(r -> projectId.equals(r.getProjectId()) && tenantId.equals(r.getTenantId()))
                .flatMap(r -> pricingRunRepository.delete(r).thenReturn(true))
                .switchIfEmpty(Mono.just(false));
    }

    /**
     * Delete every pricing run for a project. Scoped by tenantId for access control.
     *
     * @return number of records removed
     */
    public Mono<Integer> deleteAllForProject(String projectId, String tenantId) {
        return pricingRunRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .filter(r -> projectId.equals(r.getProjectId()) && tenantId.equals(r.getTenantId()))
                .collectList()
                .flatMap(list -> Flux.fromIterable(list)
                        .flatMap(pricingRunRepository::delete)
                        .then(Mono.defer(() -> Mono.just(list.size()))));
    }
}
