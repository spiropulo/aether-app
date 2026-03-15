package com.aether.app.project;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProjectRepository extends FirestoreReactiveRepository<Project> {

    Flux<Project> findAllByTenantId(String tenantId);

    Mono<Project> findByIdAndTenantId(String id, String tenantId);
}
