package com.aether.app.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Tag(name = "Subscription", description = "AI pricing subscription management")
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionRestController {

    private final SubscriptionService subscriptionService;

    public SubscriptionRestController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "Get subscription status", description = "Returns plan, usage count, limit, and cycle end for the tenant.")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getStatus(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId) {
        return subscriptionService.getStatus(tenantId)
                .map(s -> Map.<String, Object>of(
                        "plan", s.plan(),
                        "used", s.used(),
                        "limit", s.limit(),
                        "cycleEnd", s.cycleEnd() != null ? s.cycleEnd().toString() : null,
                        "priceUsd", s.priceUsd()
                ));
    }

    @Operation(summary = "Subscribe to a plan", description = "Subscribe to PRO, BUSINESS, or UNLIMITED. Starts a new billing cycle.")
    @PostMapping(value = "/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> subscribe(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId,
            @Parameter(description = "Plan: PRO, BUSINESS, UNLIMITED", required = true) @RequestParam("plan") String planStr) {
        SubscriptionPlan plan = SubscriptionPlan.fromString(planStr);
        if (plan == SubscriptionPlan.FREE) {
            return Mono.just(Map.of("error", "Cannot subscribe to FREE. Use PRO, BUSINESS, or UNLIMITED."));
        }
        return subscriptionService.subscribe(tenantId, plan)
                .map(sub -> Map.<String, Object>of(
                        "plan", sub.getPlan().name(),
                        "billingCycleEnd", sub.getBillingCycleEnd() != null ? sub.getBillingCycleEnd().toString() : null
                ));
    }

    @Operation(summary = "Cancel subscription", description = "Cancels at end of billing cycle. Retains access until then.")
    @PostMapping(value = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> cancel(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId) {
        return subscriptionService.cancel(tenantId)
                .map(sub -> Map.<String, Object>of(
                        "cancelledAt", sub.getCancelledAt() != null ? sub.getCancelledAt().toString() : null,
                        "billingCycleEnd", sub.getBillingCycleEnd() != null ? sub.getBillingCycleEnd().toString() : null
                ));
    }
}
