package com.aether.app.estimate;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PricingRunRepository extends FirestoreReactiveRepository<PricingRunRecord> {

    Flux<PricingRunRecord> findAllByProjectIdAndTenantId(String projectId, String tenantId);
}
