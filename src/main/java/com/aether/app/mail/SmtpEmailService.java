package com.aether.app.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sends emails via configured SMTP (JavaMailSender).
 */
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress != null && !fromAddress.isBlank() ? fromAddress : "noreply@localhost";
    }

    @Override
    public Mono<Void> send(String[] to, String subject, String body) {
        if (to == null || to.length == 0) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(fromAddress);
                    msg.setTo(to);
                    msg.setSubject(subject);
                    msg.setText(body);
                    mailSender.send(msg);
                    log.debug("Sent email to {}: {}", String.join(", ", to), subject);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
