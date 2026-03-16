package com.aether.app.subscription;

import com.aether.app.mail.EmailService;
import com.aether.app.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Sends billing reminder emails 5 days before each billing date.
 * Tenants can opt out via billingReminderOptOut (default: false = reminders ON).
 */
@Service
public class BillingReminderService {

    private static final Logger log = LoggerFactory.getLogger(BillingReminderService.class);
    private static final int DAYS_BEFORE_BILLING = 5;

    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    public BillingReminderService(TenantSubscriptionRepository subscriptionRepository,
                                   TenantRepository tenantRepository,
                                   EmailService emailService) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.emailService = emailService;
    }

    /**
     * Find subscriptions due for reminder (billing in 5 days) and send emails.
     */
    public Mono<Integer> sendDueReminders() {
        Instant now = Instant.now();
        Instant targetDay = now.plus(DAYS_BEFORE_BILLING, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant targetDayEnd = targetDay.plus(1, ChronoUnit.DAYS);

        return subscriptionRepository.findAll()
                .filter(sub -> sub.getPlan() != SubscriptionPlan.FREE && sub.getPlan().getPriceUsd() > 0)
                .filter(sub -> sub.getCancelledAt() == null)
                .filter(sub -> Boolean.FALSE.equals(sub.getBillingReminderOptOut()))
                .filter(sub -> {
                    Instant end = sub.getBillingCycleEnd();
                    if (end == null) return false;
                    Instant billingDay = end.truncatedTo(ChronoUnit.DAYS);
                    return !billingDay.isBefore(targetDay) && billingDay.isBefore(targetDayEnd);
                })
                .flatMap(sub -> sendReminderFor(sub).thenReturn(1))
                .reduce(0, Integer::sum)
                .doOnNext(count -> {
                    if (count > 0) log.info("Sent {} billing reminder email(s)", count);
                });
    }

    private Mono<Void> sendReminderFor(TenantSubscription sub) {
        return tenantRepository.findAllByTenantId(sub.getTenantId()).next()
                .flatMap(tenant -> {
                    String to = tenant.getEmail();
                    if (to == null || to.isBlank()) return Mono.empty();

                    String planName = sub.getPlan().name();
                    int amount = sub.getPlan().getPriceUsd();
                    String dateStr = sub.getBillingCycleEnd() != null
                            ? sub.getBillingCycleEnd().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            : "your next billing date";

                    String subject = "Your Aether subscription renews in 5 days";
                    String body = String.format(
                            "Hi%s,%n%n" +
                                    "This is a reminder that your %s plan ($%d/month) will renew on %s.%n%n" +
                                    "The charge will be applied to your payment method on file.%n%n" +
                                    "You can manage your subscription and billing preferences in Settings.%n%n" +
                                    "— Aether",
                            tenant.getDisplayName() != null ? " " + tenant.getDisplayName() : "",
                            planName, amount, dateStr);

                    return emailService.send(new String[]{to}, subject, body);
                })
                .then();
    }
}
