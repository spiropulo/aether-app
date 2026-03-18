package com.aether.app.estimate;

import com.aether.app.offer.Offer;
import com.aether.app.offer.OfferService;
import com.aether.app.project.ProjectService;
import com.aether.app.project.UpdateProjectInput;
import com.aether.app.estimate.ProjectExportService;
import com.aether.app.subscription.SubscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(
        name = "Schema Mapper Agent",
        description = "Upload a PDF document. The file is stored in Google Cloud Storage, " +
                "a record is created in Firestore, and a Pub/Sub event triggers the AI agent " +
                "to parse the PDF, map every field to the GraphQL schema, and execute the required mutations."
)
@RestController
@RequestMapping("/api/v1/estimate")
public class EstimateRestController {

    private static final Logger log = LoggerFactory.getLogger(EstimateRestController.class);
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB

    private final EstimateService estimateService;
    private final StorageService storageService;
    private final ProjectService projectService;
    private final ProjectExportService projectExportService;
    private final SubscriptionService subscriptionService;
    private final TrainingContextService trainingContextService;
    private final TenantAdaptiveAgentClient tenantAdaptiveAgentClient;
    private final PricingRunService pricingRunService;
    private final OfferService offerService;
    private final ObjectMapper objectMapper;

    @Value("${aether.pubsub.estimate-topic:}")
    private String estimateTopic;

    public EstimateRestController(EstimateService estimateService,
                                  StorageService storageService,
                                  ProjectService projectService,
                                  ProjectExportService projectExportService,
                                  SubscriptionService subscriptionService,
                                  TrainingContextService trainingContextService,
                                  TenantAdaptiveAgentClient tenantAdaptiveAgentClient,
                                  PricingRunService pricingRunService,
                                  OfferService offerService,
                                  ObjectMapper objectMapper) {
        this.estimateService = estimateService;
        this.storageService = storageService;
        this.projectService = projectService;
        this.projectExportService = projectExportService;
        this.subscriptionService = subscriptionService;
        this.trainingContextService = trainingContextService;
        this.tenantAdaptiveAgentClient = tenantAdaptiveAgentClient;
        this.pricingRunService = pricingRunService;
        this.offerService = offerService;
        this.objectMapper = objectMapper;
    }

    private static boolean isTrainingContextEmpty(String trimmed, ObjectMapper objectMapper) {
        if (trimmed == null || trimmed.isBlank()) return true;
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root == null || !root.isObject()) return true;
            boolean catalogEmpty = isEmptyArray(root.get("catalogEntries"));
            boolean tenantCustomEmpty = isEmptyArray(root.get("tenantCustomEntries"));
            boolean projectCustomEmpty = isEmptyArray(root.get("projectCustomEntries"));
            return catalogEmpty && tenantCustomEmpty && projectCustomEmpty;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isEmptyArray(JsonNode node) {
        return node == null || !node.isArray() || node.isEmpty();
    }

    @Operation(
            summary = "Process a PDF → GraphQL schema",
            operationId = "process_estimate_api_v1_estimate_process_post",
            description = """
                    Upload any PDF document as `multipart/form-data`. The service will:

                    1. **Validate** the file (PDF only, max 20 MB).
                    2. **Store** the PDF in Google Cloud Storage.
                    3. **Create** a tracking record in Firestore with file name, location, size, upload date, and uploader.
                    4. **Publish** a Pub/Sub event so the AI agent can process the document asynchronously.
                    5. **Return** an immediate acknowledgment — no need to wait for AI processing to finish.

                    > The AI agent will parse the PDF, fetch the live GraphQL schema, map every field to the correct \
                    input types, and execute the required mutations (`createProject` → `createTask` → `createOffer`).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "PDF accepted — uploaded to GCS, record created, processing queued via Pub/Sub.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PdfUploadAcknowledgment.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate filename — a file with this name already exists for this tenant.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"detail\":\"A file named 'quote.pdf' already exists for this tenant.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Validation error — not a PDF, or the file is empty.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "not_a_pdf", summary = "Non-PDF file", value = "{\"detail\":\"Only PDF files are accepted.\"}"),
                                    @ExampleObject(name = "empty_file", summary = "Empty file", value = "{\"detail\":\"The uploaded file is empty.\"}")
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "File too large (> 20 MB).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"detail\":\"File exceeds the 20 MB limit (22.3 MB).\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "GCS upload or Firestore write failed.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"detail\":\"Upload failed: <error detail>\"}")
                    )
            )
    })
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> process(
            @Parameter(
                    description = "PDF document to process. Must be a valid PDF with selectable text. Maximum size: **20 MB**.",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("file") Part filePart,

            @Parameter(
                    description = "Tenant ID to associate all created records with. Falls back to `\"default\"` if omitted.",
                    example = "tenant-123"
            )
            @RequestParam(value = "tenant_id", required = false) String tenantId,

            @Parameter(
                    description = "Identifier of the person or system performing the upload.",
                    example = "user-abc"
            )
            @RequestParam(value = "uploaded_by", required = false) String uploadedBy) {

        String filename = filePart instanceof FilePart
                ? ((FilePart) filePart).filename()
                : (filePart.headers().getContentDisposition() != null
                        ? filePart.headers().getContentDisposition().getFilename()
                        : "estimate.pdf");
        final String finalFilename = (filename == null || filename.isBlank()) ? "estimate.pdf" : filename;

        final String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/pdf";

        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> {
                    if (bytes.length == 0) {
                        return Mono.just(ResponseEntity.unprocessableEntity()
                                .<Object>body(Map.of("detail", "The uploaded file is empty.")));
                    }

                    if (bytes.length > MAX_FILE_SIZE_BYTES) {
                        double sizeMb = bytes.length / (1024.0 * 1024.0);
                        return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .<Object>body(Map.of("detail",
                                        String.format("File exceeds the 20 MB limit (%.1f MB).", sizeMb))));
                    }

                    if (!isPdf(finalFilename, contentType, bytes)) {
                        return Mono.just(ResponseEntity.unprocessableEntity()
                                .<Object>body(Map.of("detail", "Only PDF files are accepted.")));
                    }

                    log.info("PDF upload received: filename={}, size={} bytes, tenantId={}, uploadedBy={}",
                            finalFilename, bytes.length, tenantId, uploadedBy);

                    return estimateService.process(bytes, finalFilename, "application/pdf", tenantId, uploadedBy)
                            .map(ack -> ResponseEntity.accepted().<Object>body(ack))
                            .onErrorResume(DuplicateFileNameException.class, ex -> Mono.just(
                                    ResponseEntity.status(HttpStatus.CONFLICT)
                                            .<Object>body(Map.of("detail", ex.getMessage()))))
                            .onErrorResume(ex -> {
                                    log.error("PDF upload failed", ex);
                                    return Mono.just(
                                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                    .<Object>body(Map.of("detail", "Upload failed: " + ex.getMessage())));
                            });
                });
    }

    @Operation(
            summary = "Request agentic project pricing",
            operationId = "price_project_api_v1_estimate_price_project_post",
            description = """
                    Triggers the Tenant-Adaptive agent to price a project using tenant and project training data.
                    The app aggregates tenant-level (catalog + custom) and project-level training data,
                    sends it to the AI, which enriches the project via GraphQL mutations.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pricing completed. Returns agent report and tool call log."),
            @ApiResponse(responseCode = "400", description = "No training data configured. Add tenant or project training data first.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = "{\"detail\":\"Configure tenant or project training data before requesting pricing.\"}"))),
            @ApiResponse(responseCode = "404", description = "Project not found or access denied."),
            @ApiResponse(responseCode = "503", description = "Tenant-Adaptive agent not configured or unavailable.")
    })
    @PostMapping(value = "/price-project", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> priceProject(
            @Parameter(description = "Tenant ID", required = true, example = "tenant-123")
            @RequestParam(value = "tenant_id") String tenantId,
            @Parameter(description = "Project ID to price", required = true, example = "proj-abc")
            @RequestParam(value = "project_id") String projectId) {
        return projectService.getProject(projectId, tenantId)
                .flatMap(project -> {
                    // Require project address for location-based pricing
                    boolean hasAddress = (project.getAddressLine1() != null && !project.getAddressLine1().isBlank())
                            || (project.getCity() != null && !project.getCity().isBlank()
                                    && project.getCountry() != null && !project.getCountry().isBlank());
                    if (!hasAddress) {
                        return Mono.just(ResponseEntity.<Object>status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("detail", "Set the project address before requesting pricing. Edit the project and add at least street address or city + country.")));
                    }
                    return subscriptionService.canUseAiPricing(tenantId)
                            .flatMap(canUse -> {
                                if (!Boolean.TRUE.equals(canUse)) {
                                    return Mono.just(ResponseEntity.<Object>status(HttpStatus.TOO_MANY_REQUESTS)
                                            .body(Map.of("detail", "AI pricing limit reached for this billing period. Upgrade your plan or wait until the next cycle.")));
                                }
                                if (!tenantAdaptiveAgentClient.isConfigured()) {
                                    return Mono.just(ResponseEntity.<Object>status(HttpStatus.SERVICE_UNAVAILABLE)
                                            .body(Map.of("detail", "Tenant-Adaptive agent is not configured. Set aether.agent.tenant-adaptive-url.")));
                                }
                                return trainingContextService.getTrainingContextForProject(tenantId, projectId)
                                        .flatMap(trainingJson -> {
                                            if (trainingJson == null || trainingJson.isBlank() || "{}".equals(trainingJson.trim())) {
                                                return Mono.just(ResponseEntity.<Object>status(HttpStatus.BAD_REQUEST)
                                                        .body(Map.of("detail", "Configure tenant or project training data before requesting pricing.")));
                                            }
                                            String trimmed = trainingJson.trim();
                                            if (isTrainingContextEmpty(trimmed, objectMapper)) {
                                                return Mono.just(ResponseEntity.<Object>status(HttpStatus.BAD_REQUEST)
                                                        .body(Map.of("detail", "Configure tenant or project training data before requesting pricing.")));
                                            }
                                            String previousStatus = project.getStatus();
                                            UpdateProjectInput pricingInput = new UpdateProjectInput();
                                            pricingInput.setStatus("PRICING");
                                            UpdateProjectInput restoreInput = new UpdateProjectInput();
                                            restoreInput.setStatus(previousStatus != null && !previousStatus.isBlank() ? previousStatus : "Active");
                                            return projectService.updateProject(projectId, tenantId, pricingInput)
                                                    .flatMap(updated -> tenantAdaptiveAgentClient.processPricing(trainingJson, tenantId, projectId)
                                                            .flatMap(agentResponse -> subscriptionService.recordUsage(tenantId)
                                                                    .thenReturn(agentResponse))
                                                            .flatMap(agentResponse -> {
                                                                try {
                                                                    Object parsed = objectMapper.readValue(agentResponse, Object.class);
                                                                    // Persist pricing run report for UI visibility
                                                                    if (parsed instanceof Map<?, ?> m) {
                                                                        Object ar = m.get("agent_report");
                                                                        String agentReport = ar instanceof String s ? s : (ar != null ? ar.toString() : null);
                                                                        Object toolCallLogObj = m.get("tool_call_log");
                                                                        String toolCallLogJson = toolCallLogObj != null ? objectMapper.writeValueAsString(toolCallLogObj) : null;
                                                                        Object agentActivityLogObj = m.get("agent_activity_log");
                                                                        String agentActivityLogJson = agentActivityLogObj != null ? objectMapper.writeValueAsString(agentActivityLogObj) : null;
                                                                        Object tcm = m.get("tool_calls_made");
                                                                        final Integer toolCallsMade = tcm instanceof Number n ? n.intValue() : null;
                                                                        return offerService.getOffersByProject(tenantId, projectId)
                                                                                .map(offers -> {
                                                                                    List<Map<String, Object>> snapshot = new ArrayList<>();
                                                                                    double projectTotal = 0.0;
                                                                                    for (Offer o : offers) {
                                                                                        double total = o.getTotal() != null ? o.getTotal() : (o.getQuantity() != null && o.getUnitCost() != null ? o.getQuantity() * o.getUnitCost() : 0.0);
                                                                                        projectTotal += total;
                                                                                        Map<String, Object> entry = new LinkedHashMap<>();
                                                                                        entry.put("taskId", o.getTaskId());
                                                                                        entry.put("name", o.getName());
                                                                                        entry.put("quantity", o.getQuantity());
                                                                                        entry.put("unitCost", o.getUnitCost());
                                                                                        entry.put("total", total);
                                                                                        entry.put("uom", o.getUom());
                                                                                        snapshot.add(entry);
                                                                                    }
                                                                                    Map<String, Object> wrapper = new LinkedHashMap<>();
                                                                                    wrapper.put("offers", snapshot);
                                                                                    wrapper.put("projectTotal", projectTotal);
                                                                                    try {
                                                                                        return objectMapper.writeValueAsString(wrapper);
                                                                                    } catch (Exception e) {
                                                                                        return "{}";
                                                                                    }
                                                                                })
                                                                                .flatMap(offersSnapshot -> {
                                                                                    String report = PricingRunReportBuilder.build(
                                                                                            agentReport, offersSnapshot, toolCallsMade,
                                                                                            java.time.Instant.now(), objectMapper);
                                                                                    return pricingRunService.save(tenantId, projectId, agentReport, toolCallLogJson, agentActivityLogJson, toolCallsMade, offersSnapshot, report);
                                                                                })
                                                                                .thenReturn(ResponseEntity.ok().<Object>body(parsed));
                                                                    }
                                                                    return Mono.just(ResponseEntity.ok().<Object>body(parsed));
                                                                } catch (Exception e) {
                                                                    log.warn("Could not parse agent response as JSON, returning raw: {}", e.getMessage());
                                                                    return Mono.just(ResponseEntity.ok().<Object>body(Map.of("agent_report", agentResponse)));
                                                                }
                                                            })
                                                            .doFinally(signal -> projectService.updateProject(projectId, tenantId, restoreInput).subscribe())
                                                            .onErrorResume(ex -> {
                                                                String detail = "Pricing failed: " + ex.getMessage();
                                                                if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wce) {
                                                                    try {
                                                                        JsonNode body = objectMapper.readTree(wce.getResponseBodyAsString());
                                                                        if (body != null && body.has("detail")) {
                                                                            detail = body.get("detail").asText();
                                                                        }
                                                                    } catch (Exception ignored) { /* use default */ }
                                                                }
                                                                return Mono.just(ResponseEntity.<Object>status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("detail", detail)));
                                                            }));
                                        });
                            });
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("detail", "Project not found or access denied."))));
    }

    @Operation(
            summary = "Export project as PDF",
            operationId = "export_project_api_v1_estimate_projects_id_export_get",
            description = "Generates a professionally formatted PDF of the project (name, description, tasks, offers, totals). " +
                    "Available when the project is Fully Priced (all offers have unit cost) or whenever the tenant manually requests it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF file."),
            @ApiResponse(responseCode = "404", description = "Project not found or access denied.")
    })
    @GetMapping(value = "/projects/{projectId}/export", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<Object>> exportProject(
            @Parameter(description = "Project ID", required = true) @PathVariable String projectId,
            @Parameter(description = "Tenant ID", required = true, example = "tenant-123")
            @RequestParam(value = "tenant_id") String tenantId) {
        return projectExportService.generatePdf(projectId, tenantId)
                .map(bytes -> {
                    String fileName = "project-estimate.pdf";
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", fileName);
                    return ResponseEntity.ok()
                            .headers(headers)
                            .<Object>body(bytes);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Object>body(Map.of("detail", "Project not found or access denied."))));
    }

    @Operation(
            summary = "Agent health check",
            operationId = "health_check_api_v1_estimate_health_get",
            description = "Returns the current configuration of the estimate upload service, " +
                    "including the GCS bucket and Pub/Sub topic in use."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Service is healthy.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EstimateHealthResponse.class)
            )
    )
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<EstimateHealthResponse> health() {
        return Mono.just(new EstimateHealthResponse("ok", storageService.getBucketName(), estimateTopic));
    }

    @Operation(
            summary = "List PDF uploads for a tenant",
            operationId = "list_uploads_api_v1_estimate_uploads_get",
            description = "Returns all PDF uploads for the given tenant, ordered by upload date (newest first)."
    )
    @GetMapping(value = "/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> listUploads(
            @Parameter(description = "Tenant ID", required = true, example = "tenant-123")
            @RequestParam(value = "tenant_id") String tenantId) {
        return estimateService.listByTenant(tenantId)
                .collectList()
                .map(all -> all.stream()
                        .sorted((a, b) -> {
                            if (a.getUploadedAt() == null) return 1;
                            if (b.getUploadedAt() == null) return -1;
                            return b.getUploadedAt().compareTo(a.getUploadedAt());
                        })
                        .collect(Collectors.toList()))
                .map(all -> ResponseEntity.ok().<Object>body(all));
    }

    @Operation(
            summary = "Get upload record by ID",
            operationId = "get_upload_api_v1_estimate_uploads_id_get",
            description = "Returns the upload record (including runContext and agentActivityLog) for UI display."
    )
    @GetMapping(value = "/uploads/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> getUpload(
            @Parameter(description = "Upload record ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Tenant ID", required = true, example = "tenant-123")
            @RequestParam(value = "tenant_id") String tenantId) {
        return estimateService.getUploadRecord(id, tenantId)
                .map(record -> ResponseEntity.<Object>ok(record))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Object>body(Map.of("detail", "Upload not found or access denied."))));
    }

    @Operation(
            summary = "Download a PDF by upload ID",
            operationId = "download_pdf_api_v1_estimate_uploads_id_download_get",
            description = "Returns the PDF file bytes. Requires tenant_id to verify access."
    )
    @GetMapping(value = "/uploads/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<Object>> downloadPdf(
            @Parameter(description = "Upload record ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Tenant ID", required = true, example = "tenant-123")
            @RequestParam(value = "tenant_id") String tenantId) {
        return estimateService.getUploadRecord(id, tenantId)
                .flatMap(record -> estimateService.downloadPdf(id, tenantId)
                        .map(bytes -> {
                            String fileName = record.getFileName() != null ? record.getFileName() : "estimate.pdf";
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_PDF);
                            headers.setContentDispositionFormData("attachment", fileName);
                            return ResponseEntity.ok()
                                    .headers(headers)
                                    .<Object>body(bytes);
                        }))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Object>body(Map.of("detail", "PDF not found or access denied."))))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .<Object>body(Map.of("detail", "PDF not found or access denied."))));
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private boolean isPdf(String fileName, String contentType, byte[] bytes) {
        boolean nameMatch = fileName != null && fileName.toLowerCase().endsWith(".pdf");
        boolean typeMatch = "application/pdf".equals(contentType);
        // PDF magic bytes: %PDF  (0x25 0x50 0x44 0x46)
        boolean magicMatch = bytes.length >= 4
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46;
        return nameMatch || typeMatch || magicMatch;
    }

}
