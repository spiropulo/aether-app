package com.aether.app.offer;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OfferRepository extends FirestoreReactiveRepository<Offer> {

    Flux<Offer> findAllByTaskIdAndProjectIdAndTenantId(String taskId, String projectId, String tenantId);

    Flux<Offer> findAllByProjectIdAndTenantId(String projectId, String tenantId);

    /** Offers in the tenant where {@code assigneeIds} contains the given user profile id (Firestore {@code array_contains}). */
    Flux<Offer> findAllByTenantIdAndAssigneeIdsContaining(String tenantId, String assigneeId);

    Mono<Offer> findByIdAndTaskIdAndProjectIdAndTenantId(String id, String taskId, String projectId, String tenantId);
}
