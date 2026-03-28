package com.aether.app.estimate;

import java.time.Duration;
import java.util.Map;

import com.aether.app.config.AgentGoogleIdTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Calls Aether AI to parse natural-language pricing text into structured facts.
 */
@Component
public class PricingParseClient {

    private static final Logger log = LoggerFactory.getLogger(PricingParseClient.class);

    private final WebClient webClient;
    private final String parseUrl;

    public PricingParseClient(AgentGoogleIdTokenFilter agentGoogleIdTokenFilter,
                              @Value("${aether.agent.pricing-parse-url:}") String parseUrl) {
        this.parseUrl = parseUrl != null ? parseUrl.strip() : "";
        if (this.parseUrl.isBlank()) {
            this.webClient = null;
        } else {
            WebClient.Builder wb = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofMinutes(2))));
            if (agentGoogleIdTokenFilter.isEnabled()) {
                wb = wb.filter(agentGoogleIdTokenFilter);
            }
            this.webClient = wb.build();
        }
    }

    public boolean isConfigured() {
        return webClient != null && !parseUrl.isBlank();
    }

    /**
     * POST JSON body {@code { "text", "project_type_hint", "unit_hint" }} → raw JSON string response.
     */
    public Mono<String> parsePricingText(String text, String projectTypeHint, String unitHint) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException("aether.agent.pricing-parse-url is not configured"));
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("text", text != null ? text : "");
        if (projectTypeHint != null && !projectTypeHint.isBlank()) {
            body.put("project_type_hint", projectTypeHint);
        }
        if (unitHint != null && !unitHint.isBlank()) {
            body.put("unit_hint", unitHint);
        }
        return webClient.post()
                .uri(parseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Pricing parse failed: {}", e.getMessage()))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new IllegalStateException("Pricing parse service error: " + ex.getStatusCode(), ex));
    }
}
