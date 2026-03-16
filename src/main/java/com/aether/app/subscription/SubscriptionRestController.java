package com.aether.app.subscription;

import com.aether.app.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Subscription", description = "AI pricing subscription management")
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionRestController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final SubscriptionCheckoutService checkoutService;
    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository subscriptionRepository;

    @Value("${aether.stripe.publishable-key:}")
    private String stripePublishableKey;

    public SubscriptionRestController(SubscriptionService subscriptionService,
                                       StripeService stripeService,
                                       SubscriptionCheckoutService checkoutService,
                                       TenantRepository tenantRepository,
                                       TenantSubscriptionRepository subscriptionRepository) {
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
        this.checkoutService = checkoutService;
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Operation(summary = "Get subscription status", description = "Returns plan, usage count, limit, cycle end, billing reminder preference.")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getStatus(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId) {
        return subscriptionService.getStatus(tenantId)
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("plan", s.plan());
                    m.put("used", s.used());
                    m.put("limit", s.limit());
                    m.put("cycleEnd", s.cycleEnd() != null ? s.cycleEnd().toString() : null);
                    m.put("priceUsd", s.priceUsd());
                    m.put("billingReminderOptOut", s.billingReminderOptOut() != null ? s.billingReminderOptOut() : false);
                    m.put("paymentMethodLast4", s.paymentMethodLast4());
                    m.put("paymentMethodBrand", s.paymentMethodBrand());
                    m.put("stripeConfigured", stripeService.isConfigured());
                    m.put("cancelledAt", s.cancelledAt() != null ? s.cancelledAt().toString() : null);
                    return m;
                });
    }

    @Operation(summary = "Create Stripe Checkout Session for upgrade", description = "When upgrading from Free, returns Stripe Checkout URL. User enters card on Stripe.")
    @PostMapping(value = "/checkout-session", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> createCheckoutSession(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId,
            @Parameter(description = "Plan: PRO, BUSINESS, UNLIMITED", required = true) @RequestParam("plan") String planStr,
            @RequestParam("success_url") String successUrl,
            @RequestParam("cancel_url") String cancelUrl) {
        SubscriptionPlan plan = SubscriptionPlan.fromString(planStr);
        if (plan == SubscriptionPlan.FREE || plan.getPriceUsd() <= 0) {
            return Mono.just(Map.of("error", "Choose a paid plan: PRO, BUSINESS, or UNLIMITED."));
        }
        if (!stripeService.isConfigured()) {
            return Mono.just(Map.of("error", "Payment is not configured. Contact support."));
        }
        return tenantRepository.findAllByTenantId(tenantId).next()
                .flatMap(tenant -> {
                    try {
                        var session = stripeService.createCheckoutSession(
                                tenantId, plan, successUrl, cancelUrl, tenant.getEmail());
                        return Mono.just(Map.<String, Object>of(
                                "url", session.getUrl(),
                                "sessionId", session.getId()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Tenant not found")));
    }

    @Operation(summary = "Confirm upgrade after Stripe Checkout", description = "Call after user returns from Stripe. Syncs subscription from session.")
    @PostMapping(value = "/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> confirmCheckout(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId,
            @Parameter(description = "Stripe Checkout Session ID from success redirect", required = true) @RequestParam("session_id") String sessionId) {
        return checkoutService.confirmCheckout(tenantId, sessionId)
                .map(sub -> Map.<String, Object>of(
                        "plan", sub.getPlan().name(),
                        "billingCycleEnd", sub.getBillingCycleEnd() != null ? sub.getBillingCycleEnd().toString() : null));
    }

    @Operation(summary = "Toggle billing reminder email", description = "Opt out (true) or opt in (false). Default is false = reminders ON.")
    @PatchMapping(value = "/billing-reminder", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> setBillingReminder(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId,
            @RequestParam("opt_out") boolean optOut) {
        return subscriptionService.setBillingReminderOptOut(tenantId, optOut)
                .then(Mono.just(Map.<String, Object>of("billingReminderOptOut", optOut)));
    }

    @Operation(summary = "Get Stripe publishable key", description = "For frontend Stripe.js. Use pk_test_* in dev.")
    @GetMapping(value = "/stripe-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getStripeKey() {
        return Mono.just(stripeService.getPublishableKey() != null ? stripeService.getPublishableKey() : "");
    }

    @Operation(summary = "Create billing portal session", description = "Returns URL to Stripe Customer Portal to add/update payment method.")
    @PostMapping(value = "/billing-portal", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> createBillingPortalSession(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId,
            @RequestParam("return_url") String returnUrl) {
        if (!stripeService.isConfigured()) {
            return Mono.just(Map.of("error", "Payment management is not configured. Set STRIPE_SECRET_KEY and STRIPE_PUBLISHABLE_KEY to enable updating payment methods."));
        }
        return subscriptionRepository.findById(tenantId)
                .filter(sub -> sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank())
                .flatMap(sub -> {
                    try {
                        String url = stripeService.createBillingPortalSession(sub.getStripeCustomerId(), returnUrl);
                        return Mono.just(Map.<String, Object>of("url", url));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .switchIfEmpty(Mono.just(Map.of("error", "No payment method on file. Upgrade to a paid plan first.")));
    }

    @Operation(summary = "Subscribe to a plan (no payment)", description = "Direct subscribe when Stripe not used (e.g. admin). For Free→Paid upgrade, use checkout-session.")
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

    @Operation(summary = "Resume cancelled subscription", description = "Undoes cancellation. Plan continues; next payment will be charged at cycle end.")
    @PostMapping(value = "/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> resume(
            @Parameter(description = "Tenant ID", required = true) @RequestParam("tenant_id") String tenantId) {
        return subscriptionService.resume(tenantId)
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
