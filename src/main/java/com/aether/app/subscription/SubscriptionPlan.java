package com.aether.app.subscription;

/**
 * AI pricing subscription tiers.
 * FREE: 3 AI pricings/month (default).
 * PRO: 20/month for $50.
 * BUSINESS: 50/month for $100.
 * UNLIMITED: Unlimited for $200.
 */
public enum SubscriptionPlan {
    FREE(3, 0),
    PRO(20, 50),
    BUSINESS(50, 100),
    UNLIMITED(-1, 200);

    private final int monthlyLimit;
    private final int priceUsd;

    SubscriptionPlan(int monthlyLimit, int priceUsd) {
        this.monthlyLimit = monthlyLimit;
        this.priceUsd = priceUsd;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public int getPriceUsd() {
        return priceUsd;
    }

    public boolean isUnlimited() {
        return monthlyLimit < 0;
    }

    public static SubscriptionPlan fromString(String s) {
        if (s == null || s.isBlank()) return FREE;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}
