package com.aether.app.trainingdata;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TrainingDataRepository extends FirestoreReactiveRepository<TrainingData> {

    Flux<TrainingData> findAllByTenantId(String tenantId);

    Mono<TrainingData> findByIdAndTenantId(String id, String tenantId);
}
