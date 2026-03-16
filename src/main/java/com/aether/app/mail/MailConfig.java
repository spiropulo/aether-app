package com.aether.app.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mail configuration. When {@code aether.mail.enabled} is true and {@code spring.mail.host}
 * is set, the application can send emails via SMTP.
 * <p>
 * Local development: use Mailpit (docker compose up mailpit) — host=localhost, port=1025.
 * Production: configure SendGrid (smtp.sendgrid.net:587), Mailgun (smtp.mailgun.org:587),
 * or any SMTP provider via environment variables.
 */
@Configuration
public class MailConfig {

    /**
     * When mail is disabled, provide a no-op EmailService. No SMTP connection required.
     */
    @Configuration
    @ConditionalOnProperty(name = "aether.mail.enabled", havingValue = "false", matchIfMissing = true)
    static class MailDisabledConfig {
        @Bean
        @Primary
        public EmailService emailService() {
            return new NoOpEmailService();
        }
    }

    /**
     * When mail is enabled, import Spring's mail autoconfig and wire up SmtpEmailService.
     * Requires spring.mail.host, spring.mail.port, and optionally username/password.
     */
    @Configuration
    @ConditionalOnProperty(name = "aether.mail.enabled", havingValue = "true")
    static class MailEnabledConfig {
        @Bean
        @Primary
        public EmailService emailService(
                JavaMailSender mailSender,
                @Value("${aether.mail.from:${spring.mail.username:noreply@localhost}}") String from) {
            return new SmtpEmailService(mailSender, from);
        }
    }
}
