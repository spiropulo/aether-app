package com.aether.app.subscription;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

/**
 * TenantSubscription documents use tenantId as the document ID for 1:1 mapping.
 */
@Repository
public interface TenantSubscriptionRepository extends FirestoreReactiveRepository<TenantSubscription> {
}
