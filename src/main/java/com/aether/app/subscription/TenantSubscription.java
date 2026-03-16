package com.aether.app.subscription;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.time.Instant;

/**
 * Subscription state for a tenant. One document per tenant.
 * When absent, tenant is on FREE tier with calendar-month billing.
 */
@Document(collectionName = "tenantSubscriptions")
public class TenantSubscription {

    @DocumentId
    private String id;

    /** Tenant ID - same as id for 1:1 mapping. */
    private String tenantId;

    private SubscriptionPlan plan = SubscriptionPlan.FREE;

    /** Start of current billing cycle (for paid plans). */
    private Instant billingCycleStart;

    /** End of current billing cycle (for paid plans). */
    private Instant billingCycleEnd;

    /** When user cancelled; retains access until billingCycleEnd. */
    private Instant cancelledAt;

    /** Stripe customer ID for payment. */
    private String stripeCustomerId;

    /** Last 4 digits of card for display (e.g. "4242"). */
    private String paymentMethodLast4;

    /** Card brand for display (e.g. "visa"). */
    private String paymentMethodBrand;

    /** Opt out of billing reminder email (5 days before). Default false = reminders ON. */
    private Boolean billingReminderOptOut;

    /** When we last sent payment method expiration warning (avoid spam). */
    private Instant paymentMethodExpirationWarningSentAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public Instant getBillingCycleStart() {
        return billingCycleStart;
    }

    public void setBillingCycleStart(Instant billingCycleStart) {
        this.billingCycleStart = billingCycleStart;
    }

    public Instant getBillingCycleEnd() {
        return billingCycleEnd;
    }

    public void setBillingCycleEnd(Instant billingCycleEnd) {
        this.billingCycleEnd = billingCycleEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getPaymentMethodLast4() {
        return paymentMethodLast4;
    }

    public void setPaymentMethodLast4(String paymentMethodLast4) {
        this.paymentMethodLast4 = paymentMethodLast4;
    }

    public String getPaymentMethodBrand() {
        return paymentMethodBrand;
    }

    public void setPaymentMethodBrand(String paymentMethodBrand) {
        this.paymentMethodBrand = paymentMethodBrand;
    }

    public Boolean getBillingReminderOptOut() {
        return billingReminderOptOut;
    }

    public void setBillingReminderOptOut(Boolean billingReminderOptOut) {
        this.billingReminderOptOut = billingReminderOptOut;
    }

    public Instant getPaymentMethodExpirationWarningSentAt() {
        return paymentMethodExpirationWarningSentAt;
    }

    public void setPaymentMethodExpirationWarningSentAt(Instant paymentMethodExpirationWarningSentAt) {
        this.paymentMethodExpirationWarningSentAt = paymentMethodExpirationWarningSentAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
