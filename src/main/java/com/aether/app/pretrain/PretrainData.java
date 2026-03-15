package com.aether.app.pretrain;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.time.Instant;

/**
 * A global, system-managed catalog entry of pre-built training data.
 * Seeded from files in classpath:pretrain-data/ on startup.
 * Tenants can select entries from this catalog via PretrainSelection.
 */
@Document(collectionName = "pretrainData")
public class PretrainData {

    @DocumentId
    private String id;

    private String title;
    private String trainingContent;
    private String fileName;

    private Instant createdAt;
    private Instant updatedAt;

    public PretrainData() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTrainingContent() {
        return trainingContent;
    }

    public void setTrainingContent(String trainingContent) {
        this.trainingContent = trainingContent;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
