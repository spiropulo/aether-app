package com.aether.app.subscription;

import com.aether.app.auth.UserProfileRepository;
import com.aether.app.auth.UserRole;
import com.aether.app.mail.EmailService;
import com.aether.app.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Sends email to Admins when the default payment method is within 15 days of expiring.
 * Only the default payment method is checked; non-default payment methods are ignored.
 */
@Service
public class PaymentMethodExpirationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodExpirationService.class);
    private static final int DAYS_BEFORE_EXPIRATION = 15;
    private static final long MIN_DAYS_BETWEEN_WARNINGS = 7;

    private final StripeService stripeService;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final UserProfileRepository userProfileRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    public PaymentMethodExpirationService(StripeService stripeService,
                                           TenantSubscriptionRepository subscriptionRepository,
                                           UserProfileRepository userProfileRepository,
                                           TenantRepository tenantRepository,
                                           EmailService emailService) {
        this.stripeService = stripeService;
        this.subscriptionRepository = subscriptionRepository;
        this.userProfileRepository = userProfileRepository;
        this.tenantRepository = tenantRepository;
        this.emailService = emailService;
    }

    /**
     * Check all paid subscriptions and send expiration warnings to Admins when default payment method expires within 15 days.
     */
    public Mono<Integer> sendExpirationWarnings() {
        if (!stripeService.isConfigured()) {
            return Mono.just(0);
        }
        Instant now = Instant.now();
        return subscriptionRepository.findAll()
                .filter(sub -> sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank())
                .filter(sub -> sub.getPlan() != SubscriptionPlan.FREE && sub.getPlan().getPriceUsd() > 0)
                .filter(sub -> sub.getCancelledAt() == null)
                .flatMap(sub -> checkAndSendWarning(sub, now))
                .reduce(0, (a, b) -> a + b)
                .doOnNext(count -> {
                    if (count > 0) log.info("Sent {} payment method expiration warning(s)", count);
                });
    }

    private Mono<Integer> checkAndSendWarning(TenantSubscription sub, Instant now) {
        return Mono.fromCallable(() -> stripeService.getDefaultPaymentMethodExpiration(sub.getStripeCustomerId()))
                .onErrorResume(e -> {
                    log.debug("Could not get payment method expiration for customer {}: {}", sub.getStripeCustomerId(), e.getMessage());
                    return Mono.empty();
                })
                .filter(expiry -> isExpiringWithinDays(expiry, DAYS_BEFORE_EXPIRATION))
                .filter(expiry -> shouldSendWarning(sub, now))
                .flatMap(expiry -> sendWarningToAdmins(sub, expiry)
                        .then(Mono.fromCallable(() -> {
                            sub.setPaymentMethodExpirationWarningSentAt(now);
                            sub.setUpdatedAt(now);
                            return sub;
                        }))
                        .flatMap(subscriptionRepository::save)
                        .thenReturn(1))
                .defaultIfEmpty(0);
    }

    private boolean isExpiringWithinDays(YearMonth expiry, int days) {
        Instant now = Instant.now();
        Instant expiryEnd = expiry.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        if (expiryEnd.isBefore(now)) return false; // already expired, no point warning
        long daysUntilExpiry = ChronoUnit.DAYS.between(now, expiryEnd);
        return daysUntilExpiry <= days;
    }

    private boolean shouldSendWarning(TenantSubscription sub, Instant now) {
        Instant lastSent = sub.getPaymentMethodExpirationWarningSentAt();
        if (lastSent == null) return true;
        return ChronoUnit.DAYS.between(lastSent, now) >= MIN_DAYS_BETWEEN_WARNINGS;
    }

    private Mono<Void> sendWarningToAdmins(TenantSubscription sub, YearMonth expiry) {
        return userProfileRepository.findAllByTenantId(sub.getTenantId())
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .flatMap(admin -> {
                    String subject = "Action required: Payment method expires soon";
                    String expiryStr = expiry.getMonth().toString() + " " + expiry.getYear();
                    String body = String.format(
                            "Hi%s,%n%n" +
                                    "Your organization's default payment method will expire in 15 days (%s).%n%n" +
                                    "You will not be able to continue pricing projects unless you update the payment method.%n%n" +
                                    "Please update your payment method in Settings to avoid interruption.%n%n" +
                                    "— Aether",
                            admin.getDisplayName() != null ? " " + admin.getDisplayName() : "",
                            expiryStr);
                    return emailService.send(new String[]{admin.getEmail()}, subject, body);
                })
                .then();
    }
}
