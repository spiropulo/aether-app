package com.aether.app.subscription;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Handles upgrade flow: create Stripe Checkout, confirm after return, sync subscription.
 */
@Service
public class SubscriptionCheckoutService {

    private final StripeService stripeService;
    private final TenantSubscriptionRepository subscriptionRepository;

    public SubscriptionCheckoutService(StripeService stripeService,
                                       TenantSubscriptionRepository subscriptionRepository) {
        this.stripeService = stripeService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Confirm upgrade after Stripe Checkout redirect. Retrieves session, syncs TenantSubscription.
     */
    public Mono<TenantSubscription> confirmCheckout(String tenantId, String sessionId) {
        if (!stripeService.isConfigured()) {
            return Mono.error(new IllegalStateException("Stripe is not configured."));
        }
        return Mono.fromCallable(() -> stripeService.retrieveSession(sessionId))
                .flatMap(session -> {
                    String customerId = session.getCustomer();
                    if (customerId == null || customerId.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Checkout session has no customer."));
                    }
                    String planStr = null;
                    Map<String, String> meta = session.getMetadata();
                    if (meta != null) planStr = meta.get("plan");
                    SubscriptionPlan plan = SubscriptionPlan.fromString(planStr != null ? planStr : "PRO");

                    return subscriptionRepository.findById(tenantId)
                            .switchIfEmpty(Mono.defer(() -> {
                                TenantSubscription sub = new TenantSubscription();
                                sub.setId(tenantId);
                                sub.setTenantId(tenantId);
                                return Mono.just(sub);
                            }))
                            .flatMap(sub -> {
                                sub.setStripeCustomerId(customerId);
                                sub.setPlan(plan);
                                sub.setCancelledAt(null);
                                Instant now = Instant.now();
                                sub.setBillingCycleStart(now);
                                sub.setBillingCycleEnd(now.plus(30, ChronoUnit.DAYS));
                                sub.setUpdatedAt(now);
                                if (sub.getBillingReminderOptOut() == null) {
                                    sub.setBillingReminderOptOut(false); // default: reminders ON
                                }
                                return subscriptionRepository.save(sub);
                            });
                });
    }
}
