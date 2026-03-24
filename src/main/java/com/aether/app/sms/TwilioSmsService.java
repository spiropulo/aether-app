package com.aether.app.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Sends SMS via Twilio REST API (form-encoded POST).
 */
public class TwilioSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);

    private static final int TWILIO_BODY_MAX = 1600;

    private final WebClient webClient;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsService(WebClient webClient, String accountSid, String authToken, String fromNumber) {
        this.webClient = webClient;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Mono<Void> sendSms(String toE164, String body) {
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("SMS body is required."));
        }
        if (body.length() > TWILIO_BODY_MAX) {
            return Mono.error(new IllegalArgumentException(
                    "SMS body exceeds " + TWILIO_BODY_MAX + " characters (Twilio limit)."));
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", fromNumber);
        form.add("Body", body);

        return webClient.post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(h -> h.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("Twilio SMS failed to {}: {}", toE164, e.toString()))
                .then();
    }
}
