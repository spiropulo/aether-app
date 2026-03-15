package com.aether.app.pretrain;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PretrainDataRepository extends FirestoreReactiveRepository<PretrainData> {
    // existsById and findById are inherited from FirestoreReactiveRepository
    // and use the document ID directly — no index required.
    // findAll() is also inherited and returns all documents in the collection.
}
