package com.aether.app.estimate;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class TenantAdaptiveAgentClient {

    private static final Logger log = LoggerFactory.getLogger(TenantAdaptiveAgentClient.class);

    private final WebClient webClient;
    private final String processUrl;

    public TenantAdaptiveAgentClient(WebClient.Builder webClientBuilder,
                                     @Value("${aether.agent.tenant-adaptive-url:}") String processUrl) {
        this.processUrl = processUrl != null ? processUrl.strip() : "";
        this.webClient = this.processUrl.isBlank()
                ? null
                : webClientBuilder
                        .clientConnector(new ReactorClientHttpConnector(
                                HttpClient.create().responseTimeout(Duration.ofMinutes(10))))
                        .build();
    }

    public boolean isConfigured() {
        return webClient != null && !processUrl.isBlank();
    }

    /**
     * POSTs training data to the Tenant-Adaptive agent to enrich a project with pricing.
     *
     * @param trainingDataJson Aggregated tenant + project training data (JSON string)
     * @param tenantId         Tenant ID (sent as query param)
     * @param projectId         Project ID to enrich (required)
     */
    public Mono<String> processPricing(String trainingDataJson, String tenantId, String projectId) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException("aether.agent.tenant-adaptive-url is not configured"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Mono.error(new IllegalArgumentException("project_id is required"));
        }
        if (trainingDataJson == null || trainingDataJson.isBlank()) {
            return Mono.error(new IllegalArgumentException("training_data is required and must not be empty"));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("training_data", trainingDataJson);
        body.add("project_id", projectId);

        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        String uri = UriComponentsBuilder.fromUriString(processUrl)
                .queryParam("tenant_id", effectiveTenant)
                .build()
                .toUriString();

        return webClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("Tenant-adaptive agent processed project {} successfully", projectId))
                .doOnError(e -> log.error("Tenant-adaptive agent failed for project {}: {}", projectId, e.getMessage()));
    }
}
