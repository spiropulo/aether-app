package com.aether.app.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds {@code Authorization: Bearer <Google ID token>} for outbound calls to another Cloud Run
 * service (audience = configured base URL). Uses Application Default Credentials.
 */
public class AgentGoogleIdTokenFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(AgentGoogleIdTokenFilter.class);

    private final String audience;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public AgentGoogleIdTokenFilter(String audience) {
        this.audience = audience != null ? audience.strip() : "";
    }

    public boolean isEnabled() {
        return !audience.isBlank();
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (!isEnabled()) {
            return next.exchange(request);
        }
        return Mono.fromCallable(this::bearerToken)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> {
                    ClientRequest withAuth = ClientRequest.from(request)
                            .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                            .build();
                    return next.exchange(withAuth);
                })
                .doOnError(e -> log.warn("Failed to obtain ID token for agent request: {}", e.toString()));
    }

    private String bearerToken() throws IOException {
        Cached c = cache.get();
        if (c != null && c.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return c.token;
        }
        GoogleCredentials base = GoogleCredentials.getApplicationDefault();
        if (!(base instanceof IdTokenProvider idp)) {
            throw new IllegalStateException("Application Default Credentials do not support ID tokens (IdTokenProvider).");
        }
        IdTokenCredentials creds = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idp)
                .setTargetAudience(audience)
                .build();
        creds.refresh();
        com.google.auth.oauth2.IdToken idTok = creds.getIdToken();
        if (idTok == null) {
            throw new IllegalStateException("ID token refresh did not produce a token.");
        }
        String token = idTok.getTokenValue();
        long expSec = idTok.getExpirationTime() != null
                ? idTok.getExpirationTime().getTime() / 1000L
                : Instant.now().getEpochSecond() + 3000;
        cache.set(new Cached(token, Instant.ofEpochSecond(expSec)));
        return token;
    }

    private record Cached(String token, Instant expiresAt) {}
}
