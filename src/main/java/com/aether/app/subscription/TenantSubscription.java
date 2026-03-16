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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
