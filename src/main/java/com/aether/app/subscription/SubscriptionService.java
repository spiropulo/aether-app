package com.aether.app.subscription;

import com.aether.app.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Manages AI pricing subscription plans and usage.
 * FREE: 3/month. PRO: 20/month. BUSINESS: 50/month. UNLIMITED.
 * Users can subscribe/cancel anytime. On cancel, retain access until end of billing cycle.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final AiPricingUsageRepository usageRepository;

    public SubscriptionService(TenantRepository tenantRepository,
                              TenantSubscriptionRepository subscriptionRepository,
                              AiPricingUsageRepository usageRepository) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageRepository = usageRepository;
    }

    /**
     * Check if tenant can use AI pricing (has credits remaining this cycle).
     */
    public Mono<Boolean> canUseAiPricing(String tenantId) {
        return getUsageCount(tenantId)
                .zipWith(getEffectivePlan(tenantId))
                .map(tuple -> {
                    int used = tuple.getT1();
                    SubscriptionPlan plan = tuple.getT2();
                    if (plan.isUnlimited()) return true;
                    return used < plan.getMonthlyLimit();
                });
    }

    /**
     * Get usage count for the current billing cycle.
     */
    public Mono<Integer> getUsageCount(String tenantId) {
        return getBillingCycleBounds(tenantId)
                .flatMap(bounds -> usageRepository.findAllByTenantId(tenantId)
                        .filter(u -> u.getUsedAt() != null
                                && !u.getUsedAt().isBefore(bounds.get(0))
                                && u.getUsedAt().isBefore(bounds.get(1)))
                        .count()
                        .map(Long::intValue));
    }

    /**
     * Record one AI pricing usage. Call after successful pricing.
     */
    public Mono<Void> recordUsage(String tenantId) {
        AiPricingUsage usage = new AiPricingUsage();
        usage.setTenantId(tenantId);
        usage.setUsedAt(Instant.now());
        return usageRepository.save(usage).then();
    }

    /**
     * Get effective plan (considering cancelled-but-not-expired).
     */
    public Mono<SubscriptionPlan> getEffectivePlan(String tenantId) {
        return subscriptionRepository.findById(tenantId)
                .flatMap(sub -> {
                    if (sub.getCancelledAt() != null && sub.getBillingCycleEnd() != null
                            && Instant.now().isAfter(sub.getBillingCycleEnd())) {
                        return Mono.just(SubscriptionPlan.FREE);
                    }
                    return Mono.just(sub.getPlan());
                })
                .switchIfEmpty(tenantRepository.findAllByTenantId(tenantId)
                        .next()
                        .map(t -> SubscriptionPlan.fromString(t.getSubscriptionPlan()))
                        .defaultIfEmpty(SubscriptionPlan.FREE));
    }

    /**
     * Subscribe to a plan. For paid plans, starts a new billing cycle.
     */
    public Mono<TenantSubscription> subscribe(String tenantId, SubscriptionPlan plan) {
        return subscriptionRepository.findById(tenantId)
                .switchIfEmpty(Mono.defer(() -> {
                    TenantSubscription sub = new TenantSubscription();
                    sub.setId(tenantId);
                    sub.setTenantId(tenantId);
                    return Mono.just(sub);
                }))
                .flatMap(sub -> {
                    sub.setPlan(plan);
                    sub.setCancelledAt(null);
                    Instant now = Instant.now();
                    sub.setUpdatedAt(now);
                    if (plan != SubscriptionPlan.FREE) {
                        sub.setBillingCycleStart(now);
                        sub.setBillingCycleEnd(now.plus(30, ChronoUnit.DAYS));
                    } else {
                        sub.setBillingCycleStart(null);
                        sub.setBillingCycleEnd(null);
                    }
                    return subscriptionRepository.save(sub);
                });
    }

    /**
     * Resume a cancelled subscription. Clears cancelledAt so the plan continues after the current cycle.
     * Only applies when the subscription has cancelledAt set and is still within the billing cycle.
     */
    public Mono<TenantSubscription> resume(String tenantId) {
        return subscriptionRepository.findById(tenantId)
                .filter(sub -> sub.getCancelledAt() != null)
                .flatMap(sub -> {
                    sub.setCancelledAt(null);
                    sub.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(sub);
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No cancelled subscription to resume")));
    }

    /**
     * Cancel subscription. Retains access until end of billing cycle.
     * If no TenantSubscription exists (plan came from Tenant table), create one with FREE to record cancellation.
     */
    public Mono<TenantSubscription> cancel(String tenantId) {
        return subscriptionRepository.findById(tenantId)
                .flatMap(sub -> {
                    sub.setCancelledAt(Instant.now());
                    sub.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(sub);
                })
                .switchIfEmpty(Mono.defer(() -> tenantRepository.findAllByTenantId(tenantId).next()
                        .flatMap(tenant -> {
                            TenantSubscription sub = new TenantSubscription();
                            sub.setId(tenantId);
                            sub.setTenantId(tenantId);
                            sub.setPlan(SubscriptionPlan.FREE);
                            sub.setCancelledAt(Instant.now());
                            sub.setUpdatedAt(Instant.now());
                            return subscriptionRepository.save(sub);
                        })));
    }

    /**
     * Returns [cycleStart, cycleEnd) for the current billing period.
     * FREE: calendar month. Paid: stored cycle.
     */
    private Mono<List<Instant>> getBillingCycleBounds(String tenantId) {
        return subscriptionRepository.findById(tenantId)
                .map(sub -> {
                    if (sub.getBillingCycleStart() != null && sub.getBillingCycleEnd() != null) {
                        return List.of(sub.getBillingCycleStart(), sub.getBillingCycleEnd());
                    }
                    return calendarMonthBounds();
                })
                .defaultIfEmpty(calendarMonthBounds());
    }

    private static List<Instant> calendarMonthBounds() {
        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
        ZonedDateTime start = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime end = start.plusMonths(1);
        return List.of(start.toInstant(), end.toInstant());
    }

    /**
     * Get subscription status for UI (plan, used, limit, cycle end, billing reminder, payment method).
     */
    public Mono<SubscriptionStatus> getStatus(String tenantId) {
        return getEffectivePlan(tenantId)
                .zipWith(getUsageCount(tenantId))
                .zipWith(getBillingCycleBounds(tenantId))
                .zipWith(subscriptionRepository.findById(tenantId).defaultIfEmpty(new TenantSubscription()))
                .map(tuple -> {
                    SubscriptionPlan plan = tuple.getT1().getT1().getT1();
                    int used = tuple.getT1().getT1().getT2();
                    List<Instant> bounds = tuple.getT1().getT2();
                    TenantSubscription sub = tuple.getT2();
                    return new SubscriptionStatus(
                            plan.name(),
                            used,
                            plan.isUnlimited() ? -1 : plan.getMonthlyLimit(),
                            bounds.get(1),
                            plan.getPriceUsd(),
                            sub.getBillingReminderOptOut(),
                            sub.getPaymentMethodLast4(),
                            sub.getPaymentMethodBrand(),
                            sub.getCancelledAt()
                    );
                });
    }

    public record SubscriptionStatus(String plan, int used, int limit, Instant cycleEnd, int priceUsd,
                                      Boolean billingReminderOptOut, String paymentMethodLast4, String paymentMethodBrand,
                                      Instant cancelledAt) {}

    /**
     * Update billing reminder email preference. Default is false (reminders ON).
     */
    public Mono<Void> setBillingReminderOptOut(String tenantId, boolean optOut) {
        return subscriptionRepository.findById(tenantId)
                .flatMap(sub -> {
                    sub.setBillingReminderOptOut(optOut);
                    sub.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(sub).then();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    TenantSubscription sub = new TenantSubscription();
                    sub.setId(tenantId);
                    sub.setTenantId(tenantId);
                    sub.setPlan(SubscriptionPlan.FREE);
                    sub.setBillingReminderOptOut(optOut);
                    sub.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(sub).then();
                }));
    }
}
