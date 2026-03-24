package com.aether.app.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class NoOpSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsService.class);

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Mono<Void> sendSms(String toE164, String body) {
        log.debug("SMS not configured; skipping send to {}", toE164);
        return Mono.empty();
    }
}
