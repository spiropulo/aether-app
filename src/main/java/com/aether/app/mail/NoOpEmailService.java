package com.aether.app.mail;

import reactor.core.publisher.Mono;

/**
 * No-op implementation when mail is disabled. Logs instead of sending.
 */
public class NoOpEmailService implements EmailService {

    @Override
    public Mono<Void> send(String[] to, String subject, String body) {
        return Mono.empty();
    }
}
