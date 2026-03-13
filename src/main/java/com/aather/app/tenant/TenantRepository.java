package com.aather.app.tenant;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TenantRepository extends FirestoreReactiveRepository<Tenant> {

    Flux<Tenant> findAllByTenantId(String tenantId);

    Mono<Tenant> findByIdAndTenantId(String id, String tenantId);
}
