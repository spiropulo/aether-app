package com.aether.app.subscription;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.CustomerRetrieveParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.YearMonth;
import java.util.Map;

/**
 * Stripe integration for subscription payments.
 * Use sk_test_* and pk_test_* for development — test card 4242 4242 4242 4242 works.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${aether.stripe.secret-key:}")
    private String secretKey;

    @Value("${aether.stripe.publishable-key:}")
    private String publishableKey;

    @PostConstruct
    void init() {
        int len = secretKey != null ? secretKey.length() : 0;
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            log.info("Stripe initialized (test mode: {}, secret-key length: {}). Payment management enabled.", isTestMode(), len);
        } else {
            log.warn("Stripe not configured. aether.stripe.secret-key length={}. Add literal keys in application.yml or set STRIPE_SECRET_KEY.", len);
        }
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public boolean isTestMode() {
        return secretKey != null && secretKey.startsWith("sk_test_");
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    /**
     * Create a Stripe Checkout Session for subscription. User enters card on Stripe's page.
     * Redirect to successUrl on success, cancelUrl on cancel.
     */
    public Session createCheckoutSession(String tenantId, SubscriptionPlan plan,
                                         String successUrl, String cancelUrl,
                                         String customerEmail) throws StripeException {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured. Set STRIPE_SECRET_KEY.");
        }
        if (plan == SubscriptionPlan.FREE || plan.getPriceUsd() <= 0) {
            throw new IllegalArgumentException("Plan must be paid: PRO, BUSINESS, or UNLIMITED");
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(tenantId)
                .putMetadata("plan", plan.name())
                .setCustomerEmail(customerEmail != null && !customerEmail.isBlank() ? customerEmail : null)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(plan.getPriceUsd() * 100L) // cents
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(plan.name() + " Plan")
                                                                .setDescription(
                                                                        plan.isUnlimited()
                                                                                ? "Unlimited AI pricings per month"
                                                                                : plan.getMonthlyLimit() + " AI pricings per month")
                                                                .build())
                                                .setRecurring(
                                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                                                .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                                                .build())
                                                .build())
                                .build());

        return Session.create(params.build());
    }

    /**
     * Retrieve a completed Checkout Session (after redirect).
     */
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    public Map<String, Object> getSessionMetadata(Session session) {
        return Map.of(
                "customerId", session.getCustomer() != null ? session.getCustomer() : "",
                "subscriptionId", session.getSubscription() != null ? session.getSubscription() : "");
    }

    /**
     * Get the default payment method's expiration (last day of month).
     * Only the default payment method is checked; other payment methods are ignored.
     * Returns null if no default payment method or customer not found.
     */
    public YearMonth getDefaultPaymentMethodExpiration(String stripeCustomerId) throws StripeException {
        if (!isConfigured() || stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return null;
        }
        Customer customer = Customer.retrieve(
                stripeCustomerId,
                CustomerRetrieveParams.builder()
                        .addExpand("invoice_settings.default_payment_method")
                        .build(),
                null);
        if (customer == null || customer.getInvoiceSettings() == null) {
            return null;
        }
        Object defaultPm = customer.getInvoiceSettings().getDefaultPaymentMethodObject();
        if (defaultPm == null || !(defaultPm instanceof PaymentMethod)) {
            return null;
        }
        PaymentMethod pm = (PaymentMethod) defaultPm;
        if (pm.getCard() == null) {
            return null;
        }
        Long expMonth = pm.getCard().getExpMonth();
        Long expYear = pm.getCard().getExpYear();
        if (expMonth == null || expYear == null) {
            return null;
        }
        return YearMonth.of(expYear.intValue(), expMonth.intValue());
    }

    /**
     * Create a Stripe Billing Portal session so the customer can update their payment method.
     */
    public String createBillingPortalSession(String stripeCustomerId, String returnUrl) throws StripeException {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured.");
        }
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(stripeCustomerId)
                        .setReturnUrl(returnUrl)
                        .build();
        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }
}
