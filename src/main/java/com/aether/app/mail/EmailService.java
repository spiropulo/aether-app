package com.aether.app.mail;

import reactor.core.publisher.Mono;

/**
 * Service for sending emails. When mail is disabled, implementations are no-op.
 * When enabled, sends via configured SMTP (Mailpit locally, SendGrid/Mailgun in production).
 */
public interface EmailService {

    /**
     * Send an email to the given recipients.
     *
     * @param to      recipient email addresses
     * @param subject email subject
     * @param body    plain-text body
     * @return Mono that completes when sent (or no-op when disabled)
     */
    Mono<Void> send(String[] to, String subject, String body);
}
