package com.aether.app.labor;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LaborRepository extends FirestoreReactiveRepository<Labor> {

    Flux<Labor> findAllByTaskIdAndProjectIdAndTenantId(String taskId, String projectId, String tenantId);

    Mono<Labor> findByIdAndTaskIdAndProjectIdAndTenantId(String id, String taskId, String projectId, String tenantId);
}
