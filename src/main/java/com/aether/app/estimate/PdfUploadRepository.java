package com.aether.app.estimate;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PdfUploadRepository extends FirestoreReactiveRepository<PdfUploadRecord> {

    Flux<PdfUploadRecord> findAllByTenantId(String tenantId);

    Mono<PdfUploadRecord> findByIdAndTenantId(String id, String tenantId);

    Flux<PdfUploadRecord> findByTenantIdAndFileName(String tenantId, String fileName);

    /** Uploads with the same file name are allowed across projects; use this for import-into-project flows. */
    Flux<PdfUploadRecord> findByTenantIdAndFileNameAndProjectId(String tenantId, String fileName, String projectId);

    Flux<PdfUploadRecord> findByTenantIdAndProjectId(String tenantId, String projectId);

    Mono<PdfUploadRecord> findFirstByProjectId(String projectId);
}
