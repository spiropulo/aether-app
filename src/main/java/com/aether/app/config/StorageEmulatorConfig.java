package com.aether.app.config;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Provides a GCS Storage client configured for the fake-gcs-server emulator when
 * {@code aether.storage.emulator-host} is set. Active only in the {@code local} profile
 * for PDF uploads without real GCP credentials.
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "aether.storage.emulator-host")
public class StorageEmulatorConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageEmulatorConfig.class);

    @Bean
    @Primary
    public Storage storage(
            @Value("${aether.storage.emulator-host}") String emulatorHost,
            @Value("${spring.cloud.gcp.project-id:aether}") String projectId) {
        log.info("Using GCS emulator at {} for local storage", emulatorHost);
        return StorageOptions.newBuilder()
                .setHost(emulatorHost)
                .setProjectId(projectId)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @Bean
    public ApplicationRunner ensureBucketExists(Storage storage,
                                                @Value("${aether.storage.bucket-name}") String bucketName) {
        return args -> {
            try {
                if (storage.get(bucketName) == null) {
                    log.info("Creating bucket {} in GCS emulator", bucketName);
                    storage.create(BucketInfo.of(bucketName));
                }
            } catch (StorageException e) {
                log.warn("Could not ensure bucket {} exists (emulator may not support bucket creation or bucket may already exist): {}",
                        bucketName, e.getMessage());
            }
        };
    }
}
