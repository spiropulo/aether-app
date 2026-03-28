package com.aether.app.estimate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aether.app.config.AgentGoogleIdTokenFilter;
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
    private final String projectPdfSyncUrl;

    public EstimateAgentClient(AgentGoogleIdTokenFilter agentGoogleIdTokenFilter,
                               @Value("${aether.agent.project-pdf-sync-url:}") String projectPdfSyncUrl) {
        this.projectPdfSyncUrl = projectPdfSyncUrl != null ? projectPdfSyncUrl.strip() : "";
        if (this.projectPdfSyncUrl.isBlank()) {
            this.webClient = null;
        } else {
            WebClient.Builder wb = WebClient.builder();
            if (agentGoogleIdTokenFilter.isEnabled()) {
                wb = wb.filter(agentGoogleIdTokenFilter);
            }
            this.webClient = wb.build();
        }
    }

    /** True when the Project PDF Sync agent URL is set (PDF import into a project). */
    public boolean isConfigured() {
        return webClient != null && !projectPdfSyncUrl.isBlank();
    }

    /**
     * POSTs the PDF to the Project PDF Sync agent (mutations locked to the given project).
     */
    public Mono<String> processProjectPdfSync(byte[] pdfBytes, String fileName, String tenantId, String projectId) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException(
                    "aether.agent.project-pdf-sync-url is not configured "
                            + "(e.g. http://localhost:8055/api/v1/project-pdf-sync/process)"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Mono.error(new IllegalArgumentException("project_id is required."));
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
        String uri = UriComponentsBuilder.fromUriString(projectPdfSyncUrl)
                .queryParam("tenant_id", effectiveTenant)
                .build()
                .toUriString();

        return webClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("Project PDF sync agent processed: {}", fileName))
                .doOnError(e -> log.error("Project PDF sync agent failed: {}", fileName, e));
    }
}
