package com.aether.app.email;

import com.aether.app.mail.EmailService;
import com.aether.app.sms.PhoneNumbers;
import com.aether.app.sms.SmsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectEmailService {

    private final ProjectEmailRepository emailRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    public ProjectEmailService(ProjectEmailRepository emailRepository,
                               EmailService emailService,
                               SmsService smsService) {
        this.emailRepository = emailRepository;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    /**
     * Send email and/or SMS for a project (or task) notification. Persists one history row.
     */
    public Mono<ProjectEmail> send(SendProjectEmailInput input) {
        String tenantId = input.getTenantId();
        String projectId = input.getProjectId();
        String taskId = input.getTaskId();
        String offerId = input.getOfferId();
        String senderId = input.getSenderId();
        List<String> toEmails = input.getToEmails() != null ? input.getToEmails() : List.of();
        List<String> rawPhones = input.getToPhoneNumbers() != null ? input.getToPhoneNumbers() : List.of();

        Boolean sendEmailFlag = input.getSendEmail();
        Boolean sendSmsFlag = input.getSendSms();
        boolean legacy = sendEmailFlag == null && sendSmsFlag == null;
        boolean doEmail = legacy ? !toEmails.isEmpty() : Boolean.TRUE.equals(sendEmailFlag);
        boolean doSms = legacy ? false : Boolean.TRUE.equals(sendSmsFlag);

        if (!doEmail && !doSms) {
            return Mono.error(new IllegalArgumentException("Choose at least one of email or text (SMS)."));
        }
        if (doSms && !smsService.isEnabled()) {
            return Mono.error(new IllegalArgumentException(
                    "Text messaging is off on the server. For local dev with Prism: set AETHER_SMS_ENABLED=true, "
                            + "TWILIO_API_BASE_URL=http://localhost:4010, and TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / "
                            + "TWILIO_FROM_NUMBER (any values work for the mock; run docker compose up -d twilio-mock). "
                            + "For production: enable SMS with real Twilio credentials and omit TWILIO_API_BASE_URL."));
        }

        String subject = input.getSubject() != null ? input.getSubject().trim() : "";
        String body = input.getBody() != null ? input.getBody() : "";

        if (doEmail) {
            if (toEmails.isEmpty()) {
                return Mono.error(new IllegalArgumentException("At least one email recipient is required for email."));
            }
            if (subject.isBlank()) {
                return Mono.error(new IllegalArgumentException("Subject is required for email."));
            }
        }

        List<String> normalizedPhones = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : rawPhones) {
            String e164 = PhoneNumbers.normalizeToE164(raw);
            if (e164 != null && seen.add(e164)) {
                normalizedPhones.add(e164);
            }
        }

        if (doSms) {
            if (normalizedPhones.isEmpty()) {
                return Mono.error(new IllegalArgumentException(
                        "At least one valid phone number is required for text (SMS)."));
            }
            if (body.isBlank()) {
                return Mono.error(new IllegalArgumentException("Message body is required for text (SMS)."));
            }
        }

        String storedSubject = doEmail ? subject : "(SMS)";

        ProjectEmail record = new ProjectEmail();
        record.setId(UUID.randomUUID().toString());
        record.setTenantId(tenantId);
        record.setProjectId(projectId);
        record.setTaskId(taskId);
        record.setOfferId(offerId);
        record.setSenderId(senderId);
        record.setToEmails(doEmail ? toEmails : List.of());
        record.setToPhoneNumbers(doSms ? normalizedPhones : List.of());
        List<String> channels = new ArrayList<>();
        if (doEmail) {
            channels.add("EMAIL");
        }
        if (doSms) {
            channels.add("SMS");
        }
        record.setDeliveryChannels(channels);
        record.setSubject(storedSubject);
        record.setBody(body);
        record.setSentAt(Instant.now());

        Mono<Void> emailMono = doEmail
                ? emailService.send(toEmails.toArray(new String[0]), subject, body)
                : Mono.empty();

        Mono<Void> smsMono = doSms
                ? Flux.fromIterable(normalizedPhones).concatMap(p -> smsService.sendSms(p, body)).then()
                : Mono.empty();

        return Mono.when(emailMono, smsMono)
                .then(emailRepository.save(record))
                .thenReturn(record);
    }

    public Flux<ProjectEmail> getEmailHistory(String projectId, String tenantId) {
        return emailRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .sort((a, b) -> {
                    Instant sa = a.getSentAt();
                    Instant sb = b.getSentAt();
                    if (sa == null && sb == null) return 0;
                    if (sa == null) return 1;
                    if (sb == null) return -1;
                    return sb.compareTo(sa); // newest first
                });
    }
}
