package com.aether.app.pretrain;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PretrainedDataRepository extends FirestoreReactiveRepository<PretrainedData> {
}
