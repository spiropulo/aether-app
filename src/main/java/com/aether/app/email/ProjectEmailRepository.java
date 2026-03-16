package com.aether.app.email;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ProjectEmailRepository extends FirestoreReactiveRepository<ProjectEmail> {

    Flux<ProjectEmail> findAllByProjectIdAndTenantId(String projectId, String tenantId);
}
