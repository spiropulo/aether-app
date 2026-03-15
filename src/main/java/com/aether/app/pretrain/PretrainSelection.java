package com.aether.app.pretrain;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.time.Instant;

/**
 * Records a tenant's choice to use a catalog PretrainData entry.
 * taskId is null for tenant-level selections (applies to all agent runs for the tenant).
 * When taskId is set, the selection applies only to agent runs for that specific task.
 */
@Document(collectionName = "pretrainSelections")
public class PretrainSelection {

    @DocumentId
    private String id;

    private String tenantId;
    private String taskId;
    private String pretrainDataId;

    private Instant createdAt;

    public PretrainSelection() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPretrainDataId() {
        return pretrainDataId;
    }

    public void setPretrainDataId(String pretrainDataId) {
        this.pretrainDataId = pretrainDataId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
