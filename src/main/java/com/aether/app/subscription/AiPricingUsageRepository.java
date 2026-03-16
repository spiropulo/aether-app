package com.aether.app.subscription;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AiPricingUsageRepository extends FirestoreReactiveRepository<AiPricingUsage> {

    Flux<AiPricingUsage> findAllByTenantId(String tenantId);
}
