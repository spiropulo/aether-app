package com.aether.app.task;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TaskRepository extends FirestoreReactiveRepository<Task> {

    Flux<Task> findAllByProjectIdAndTenantId(String projectId, String tenantId);

    Mono<Task> findByIdAndProjectIdAndTenantId(String id, String projectId, String tenantId);

    Flux<Task> findAllByTenantIdAndProjectIdAndName(String tenantId, String projectId, String name);
}
