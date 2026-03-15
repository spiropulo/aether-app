package com.aether.app.pretrain;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PretrainSelectionRepository extends FirestoreReactiveRepository<PretrainSelection> {

    Flux<PretrainSelection> findAllByTenantId(String tenantId);

    Flux<PretrainSelection> findAllByTenantIdAndTaskId(String tenantId, String taskId);

    /**
     * Used for task-level dedup check — taskId must never be null here.
     * Firestore derived queries cannot filter on null values.
     */
    Mono<PretrainSelection> findByTenantIdAndPretrainDataIdAndTaskId(String tenantId, String pretrainDataId,
                                                                      String taskId);

    /**
     * Used for tenant-level dedup check — fetches all selections for the tenant+pretrainData
     * pair regardless of taskId; the caller filters for taskId == null in-memory.
     */
    Flux<PretrainSelection> findAllByTenantIdAndPretrainDataId(String tenantId, String pretrainDataId);

    Mono<PretrainSelection> findByIdAndTenantId(String id, String tenantId);
}
