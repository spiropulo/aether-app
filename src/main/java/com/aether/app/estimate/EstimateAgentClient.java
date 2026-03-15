package com.aether.app.estimate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class EstimateAgentClient {

    private static final Logger log = LoggerFactory.getLogger(EstimateAgentClient.class);

    private final WebClient webClient;
    private final String processUrl;

    public EstimateAgentClient(WebClient.Builder webClientBuilder,
                               @Value("${aether.agent.process-url:}") String processUrl) {
        this.processUrl = processUrl != null ? processUrl.strip() : "";
        this.webClient = this.processUrl.isBlank()
                ? null
                : webClientBuilder.build();
    }

    public boolean isConfigured() {
        return webClient != null && !processUrl.isBlank();
    }

    /**
     * POSTs the PDF to the Schema Mapper agent. Inputs: file, project_id, tenant_id (query).
     * No training data — the schema-mapper only parses PDF and maps to GraphQL.
     *
     * @param pdfBytes   PDF file bytes
     * @param fileName   Original file name
     * @param tenantId   Tenant ID (sent as query param)
     * @param projectId  Project ID (required; created by aether-app upstream)
     */
    public Mono<String> processPdf(byte[] pdfBytes, String fileName, String tenantId, String projectId) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException("aether.agent.process-url is not configured"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "project_id is required by the Aether AI schema. The project must be created upstream before processing."));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return fileName != null ? fileName : "upload.pdf";
            }
        };
        body.add("file", new HttpEntity<>(fileResource, fileHeaders));
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
                .doOnSuccess(r -> log.info("Agent processed PDF successfully: {}", fileName))
                .doOnError(e -> log.error("Agent failed to process PDF: {}", fileName, e));
    }
}
