package com.aether.app.trainingdata;

import java.util.List;

public class CreateProjectTrainingDataInput {

    private String tenantId;
    private String projectId;
    private List<TrainingDataEntryInput> entries;
    private List<PricingFactInput> pricingFacts;
    private String description;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public List<TrainingDataEntryInput> getEntries() {
        return entries;
    }

    public void setEntries(List<TrainingDataEntryInput> entries) {
        this.entries = entries;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PricingFactInput> getPricingFacts() {
        return pricingFacts;
    }

    public void setPricingFacts(List<PricingFactInput> pricingFacts) {
        this.pricingFacts = pricingFacts;
    }
}
