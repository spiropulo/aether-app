package com.aether.app.auth;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserProfileRepository extends FirestoreReactiveRepository<UserProfile> {

    Flux<UserProfile> findAllByTenantId(String tenantId);

    Mono<UserProfile> findByIdAndTenantId(String id, String tenantId);

    Mono<UserProfile> findByUsernameAndTenantId(String username, String tenantId);

    Mono<UserProfile> findByEmailAndTenantId(String email, String tenantId);
}
