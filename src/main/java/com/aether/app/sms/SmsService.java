package com.aether.app.sms;

import reactor.core.publisher.Mono;

/**
 * Outbound SMS. When disabled, {@link #isEnabled()} is false and sends are rejected at the service layer.
 */
public interface SmsService {

    boolean isEnabled();

    Mono<Void> sendSms(String toE164, String body);
}
