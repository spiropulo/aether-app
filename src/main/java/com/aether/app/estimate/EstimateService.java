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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    /**
     * Upload a PDF and associate it with an existing project. Queues the Project PDF Sync agent.
     */
    public Mono<PdfUploadAcknowledgment> processForExistingProject(String projectId,
                                                                   byte[] fileBytes,
                                                                   String fileName,
                                                                   String contentType,
                                                                   String tenantId,
                                                                   String uploadedBy) {
        String effectiveTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        return projectService.getProject(projectId, effectiveTenant)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Project not found or access denied.")))
                .then(Mono.defer(() -> uploadRepository.findByTenantIdAndFileNameAndProjectId(effectiveTenant, fileName, projectId)
                        .hasElements()
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return Mono.<PdfUploadAcknowledgment>error(
                                        DuplicateFileNameException.alreadyImportedForProject(fileName));
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
                                        record.setProjectId(projectId);
                                        return uploadRepository.save(record);
                                    })
                                    .flatMap(record -> projectService.setSourcePdfUploadId(projectId, effectiveTenant, record.getId())
                                            .thenReturn(record))
                                    .doOnSuccess(this::publishProcessingEvent)
                                    .map(record -> new PdfUploadAcknowledgment(
                                            "accepted",
                                            "Your PDF is queued to import into this project.",
                                            record.getId(),
                                            record.getTenantId(),
                                            record.getFileName(),
                                            record.getFileSizeBytes(),
                                            record.getGcsPath(),
                                            record.getUploadedAt()
                                    ));
                        })));
    }

    private void publishProcessingEvent(PdfUploadRecord record) {
        if (estimateTopic == null || estimateTopic.isBlank()) {
            log.warn("aether.pubsub.estimate-topic is not configured — skipping Pub/Sub publish for record {}", record.getId());
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("recordId", record.getId());
            payload.put("tenantId", record.getTenantId());
            payload.put("projectId", record.getProjectId());
            payload.put("fileName", record.getFileName());
            payload.put("gcsPath", record.getGcsPath());
            payload.put("uploadedAt", record.getUploadedAt().toString());
            payload.put("uploadedBy", record.getUploadedBy());
            pubSubTemplate.publish(estimateTopic, objectMapper.writeValueAsString(payload));
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

    /** PDFs uploaded for a specific project, newest first. */
    public Mono<List<PdfUploadRecord>> listUploadsByProjectSorted(String tenantId, String projectId) {
        return uploadRepository.findByTenantIdAndProjectId(tenantId, projectId)
                .collectList()
                .map(this::sortUploadsNewestFirst);
    }

    private List<PdfUploadRecord> sortUploadsNewestFirst(List<PdfUploadRecord> all) {
        all.sort((a, b) -> {
            if (a.getUploadedAt() == null) {
                return 1;
            }
            if (b.getUploadedAt() == null) {
                return -1;
            }
            return b.getUploadedAt().compareTo(a.getUploadedAt());
        });
        return all;
    }

    /**
     * Deletes upload metadata and GCS object. When {@code requiredProjectIdOrNull} is set, the record must belong to that project.
     * Clears {@link com.aether.app.project.Project#getSourcePdfUploadId()} when it matches.
     */
    public Mono<Void> deleteUpload(String uploadId, String tenantId, String requiredProjectIdOrNull) {
        return uploadRepository.findByIdAndTenantId(uploadId, tenantId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Upload not found")))
                .flatMap(record -> {
                    if (requiredProjectIdOrNull != null) {
                        String pid = record.getProjectId();
                        if (pid == null || !requiredProjectIdOrNull.equals(pid)) {
                            return Mono.error(new NoSuchElementException("Upload not found"));
                        }
                    }
                    String projectIdForClear = record.getProjectId();
                    return storageService.deleteObject(record.getGcsPath())
                            .onErrorResume(ex -> {
                                log.warn("GCS delete failed for {}: {}", record.getGcsPath(), ex.toString());
                                return Mono.empty();
                            })
                            .then(uploadRepository.delete(record))
                            .then(Mono.defer(() -> projectIdForClear != null
                                    ? projectService.clearSourcePdfUploadIfMatches(projectIdForClear, tenantId, uploadId)
                                    : Mono.empty()));
                });
    }

    public Mono<PdfUploadRecord> getUploadRecord(String recordId, String tenantId) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId);
    }

    public Mono<byte[]> downloadPdf(String recordId, String tenantId) {
        return uploadRepository.findByIdAndTenantId(recordId, tenantId)
                .flatMap(record -> storageService.download(record.getGcsPath()));
    }
}
