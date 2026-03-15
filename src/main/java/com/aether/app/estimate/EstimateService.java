package com.aether.app.estimate;

import com.aether.app.project.CreateProjectInput;
import com.aether.app.project.Project;
import com.aether.app.project.ProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EstimateService {

    private static final Logger log = LoggerFactory.getLogger(EstimateService.class);

    private final StorageService storageService;
    private final PdfUploadRepository uploadRepository;
    private final ProjectService projectService;
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final String estimateTopic;
    private final String estimateFolder;

    public EstimateService(StorageService storageService,
                            PdfUploadRepository uploadRepository,
                            ProjectService projectService,
                            PubSubTemplate pubSubTemplate,
                            ObjectMapper objectMapper,
                            @Value("${aether.pubsub.estimate-topic:}") String estimateTopic,
                            @Value("${aether.storage.estimate-folder:estimates}") String estimateFolder) {
        this.storageService = storageService;
        this.uploadRepository = uploadRepository;
        this.projectService = projectService;
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.estimateTopic = estimateTopic;
        this.estimateFolder = estimateFolder;
    }

    /**
     * Full pipeline:
     * 1. Upload the PDF bytes to GCS.
     * 2. Persist a {@link PdfUploadRecord} in Firestore.
     * 3. Publish a Pub/Sub event so the AI processing service can pick it up.
     * 4. Return an async acknowledgment to the caller.
     */
    public Mono<PdfUploadAcknowledgment> process(byte[] fileBytes, String fileName,
                                                   String contentType, String tenantId,
                                                   String uploadedBy) {
        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";

        return uploadRepository.findByTenantIdAndFileName(effectiveTenant, fileName)
                .hasElements()
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<PdfUploadAcknowledgment>error(new DuplicateFileNameException(effectiveTenant, fileName));
                    }
                    String uploadId = UUID.randomUUID().toString();
                    String objectName = estimateFolder + "/" + uploadId + "/" + sanitizeFileName(fileName);
                    Instant now = Instant.now();
                    return storageService.upload(objectName, fileBytes, contentType)
                            .flatMap(gcsPath -> {
                                PdfUploadRecord record = new PdfUploadRecord();
                                record.setId(uploadId);
                                record.setTenantId(effectiveTenant);
                                record.setUploadedBy(uploadedBy != null && !uploadedBy.isBlank() ? uploadedBy : effectiveTenant);
                                record.setFileName(fileName);
                                record.setGcsPath(gcsPath);
                                record.setContentType(contentType);
                                record.setFileSizeBytes(fileBytes.length);
                                record.setStatus(UploadStatus.PENDING);
                                record.setUploadedAt(now);
                                return uploadRepository.save(record);
                            })
                            .flatMap(record -> {
                                String projectName = "PDF Estimate - " + (fileName != null ? fileName.replaceAll("\\.pdf$", "") : "document");
                                CreateProjectInput input = new CreateProjectInput();
                                input.setTenantId(effectiveTenant);
                                input.setName(projectName);
                                input.setStatus("Processing");
                                input.setSourcePdfUploadId(record.getId());
                                return projectService.createProject(input)
                                        .flatMap(project -> {
                                            record.setProjectId(project.getId());
                                            return uploadRepository.save(record)
                                                    .thenReturn(new Object[]{record, project});
                                        });
                            })
                            .doOnSuccess(arr -> {
                                PdfUploadRecord r = (PdfUploadRecord) ((Object[]) arr)[0];
                                publishProcessingEvent(r);
                            })
                            .map(arr -> {
                                PdfUploadRecord record = (PdfUploadRecord) ((Object[]) arr)[0];
                                return new PdfUploadAcknowledgment(
                                    "accepted",
                                    "Your PDF has been uploaded and is now queued for AI processing. " +
                                            "The extracted data will be consumed and available shortly.",
                                    record.getId(),
                                    record.getTenantId(),
                                    record.getFileName(),
                                    record.getFileSizeBytes(),
                                    record.getGcsPath(),
                                    record.getUploadedAt()
                            );
                            });
                });
    }

    private void publishProcessingEvent(PdfUploadRecord record) {
        if (estimateTopic == null || estimateTopic.isBlank()) {
            log.warn("aether.pubsub.estimate-topic is not configured — skipping Pub/Sub publish for record {}", record.getId());
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "recordId", record.getId(),
                    "tenantId", record.getTenantId(),
                    "projectId", record.getProjectId(),
                    "fileName", record.getFileName(),
                    "gcsPath", record.getGcsPath(),
                    "uploadedAt", record.getUploadedAt().toString(),
                    "uploadedBy", record.getUploadedBy()
            ));
            pubSubTemplate.publish(estimateTopic, payload);
            log.info("Published estimate processing event for record {} to topic {}", record.getId(), estimateTopic);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Pub/Sub payload for record {}", record.getId(), e);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.pdf";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public Mono<PdfUploadRecord> updateStatus(String recordId, String tenantId, UploadStatus status) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> {
                    record.setStatus(status);
                    return uploadRepository.save(record);
                });
    }

    public Mono<PdfUploadRecord> updateStatusAndProjectId(String recordId, String tenantId,
                                                           UploadStatus status, String projectId) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> {
                    record.setStatus(status);
                    record.setProjectId(projectId);
                    return uploadRepository.save(record);
                });
    }

    public Mono<PdfUploadRecord> updateRunContext(String recordId, String tenantId, String runContext) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> {
                    record.setRunContext(runContext);
                    return uploadRepository.save(record);
                });
    }

    public Mono<PdfUploadRecord> updateAgentActivityLog(String recordId, String tenantId, String agentActivityLog) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> {
                    record.setAgentActivityLog(agentActivityLog);
                    return uploadRepository.save(record);
                });
    }

    public Flux<PdfUploadRecord> listByTenant(String tenantId) {
        return uploadRepository.findAllByTenantId(tenantId);
    }

    public Mono<PdfUploadRecord> getUploadRecord(String recordId, String tenantId) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId);
    }

    public Mono<byte[]> downloadPdf(String recordId, String tenantId) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> storageService.download(record.getGcsPath()));
    }
}
