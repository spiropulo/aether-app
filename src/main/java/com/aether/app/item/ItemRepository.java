package com.aether.app.item;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ItemRepository extends FirestoreReactiveRepository<Item> {

    Flux<Item> findAllByTaskIdAndProjectIdAndTenantId(String taskId, String projectId, String tenantId);

    Mono<Item> findByIdAndTaskIdAndProjectIdAndTenantId(String id, String taskId, String projectId, String tenantId);
}
