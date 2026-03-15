package com.aether.app.estimate;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.WriteChannel;

import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Storage storage;
    private final String bucketName;

    public StorageService(@Autowired(required = false) Storage storage,
                          @Value("${aether.storage.bucket-name}") String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
        if (storage == null) {
            log.warn("GCS Storage bean is not available. PDF uploads will fail at request time. " +
                    "Run `gcloud auth application-default login` and remove " +
                    "`spring.cloud.gcp.storage.enabled: false` from application-local.yml to enable real GCS uploads.");
        }
    }

    /**
     * Uploads {@code content} to GCS at {@code objectName} and returns the {@code gs://} URI.
     * Runs on the bounded-elastic scheduler because the GCS client is blocking I/O.
     * Returns an error Mono if GCS is not configured (e.g., in local dev without credentials).
     */
    public Mono<String> upload(String objectName, byte[] content, String contentType) {
        if (storage == null) {
            return Mono.error(new IllegalStateException(
                    "GCS Storage is not configured in this environment. " +
                    "Run `gcloud auth application-default login` to enable PDF uploads."));
        }
        return Mono.fromCallable(() -> {
            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();
            // Use resumable upload (writer) instead of multipart - Firebase Storage emulator
            // has bugs with multipart "Missing content type" parsing
            try (WriteChannel writer = storage.writer(blobInfo)) {
                writer.write(ByteBuffer.wrap(content));
            }
            return "gs://" + bucketName + "/" + objectName;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Downloads content from GCS given a {@code gs://bucket/path} URI.
     * Returns an error Mono if GCS is not configured.
     */
    public Mono<byte[]> download(String gcsPath) {
        if (storage == null) {
            return Mono.error(new IllegalStateException("GCS Storage is not configured."));
        }
        return Mono.fromCallable(() -> {
            BlobId blobId = parseGcsPath(gcsPath);
            Blob blob = storage.get(blobId);
            if (blob == null) {
                throw new IllegalArgumentException("Blob not found: " + gcsPath);
            }
            return blob.getContent();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static BlobId parseGcsPath(String gcsPath) {
        if (gcsPath == null || !gcsPath.startsWith("gs://")) {
            throw new IllegalArgumentException("Invalid GCS path: " + gcsPath);
        }
        String withoutScheme = gcsPath.substring(5);
        int slash = withoutScheme.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid GCS path (missing object name): " + gcsPath);
        }
        String bucket = withoutScheme.substring(0, slash);
        String name = withoutScheme.substring(slash + 1);
        return BlobId.of(bucket, name);
    }

    public boolean isConfigured() {
        return storage != null;
    }

    public String getBucketName() {
        return bucketName;
    }
}
