package com.aether.app.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs daily to send billing reminder emails 5 days before each tenant's billing date,
 * and payment method expiration warnings 15 days before the default payment method expires.
 */
@Component
public class BillingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingReminderScheduler.class);

    private final BillingReminderService billingReminderService;
    private final PaymentMethodExpirationService paymentMethodExpirationService;

    public BillingReminderScheduler(BillingReminderService billingReminderService,
                                     PaymentMethodExpirationService paymentMethodExpirationService) {
        this.billingReminderService = billingReminderService;
        this.paymentMethodExpirationService = paymentMethodExpirationService;
    }

    @Scheduled(cron = "${aether.billing-reminder.cron:0 0 9 * * *}") // default: 9 AM daily
    public void runReminders() {
        log.debug("Running billing reminder job");
        billingReminderService.sendDueReminders().subscribe();
    }

    @Scheduled(cron = "${aether.payment-method-expiration.cron:0 0 10 * * *}") // default: 10 AM daily
    public void runPaymentMethodExpirationWarnings() {
        log.debug("Running payment method expiration warning job");
        paymentMethodExpirationService.sendExpirationWarnings().subscribe();
    }
}
