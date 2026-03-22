package com.aether.app.trainingdata;

/**
 * GraphQL input for {@link PricingFact}.
 */
public class PricingFactInput {

    private String id;
    private String projectType;
    private String material;
    private String unit;
    private Double priceMin;
    private Double priceMax;
    private Double pricePoint;
    private Boolean includesLabor;
    private String condition;
    private String notes;
    private String source;
    private Double confidence;
    private Integer basedOnCount;
    private String observedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getPriceMin() {
        return priceMin;
    }

    public void setPriceMin(Double priceMin) {
        this.priceMin = priceMin;
    }

    public Double getPriceMax() {
        return priceMax;
    }

    public void setPriceMax(Double priceMax) {
        this.priceMax = priceMax;
    }

    public Double getPricePoint() {
        return pricePoint;
    }

    public void setPricePoint(Double pricePoint) {
        this.pricePoint = pricePoint;
    }

    public Boolean getIncludesLabor() {
        return includesLabor;
    }

    public void setIncludesLabor(Boolean includesLabor) {
        this.includesLabor = includesLabor;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getBasedOnCount() {
        return basedOnCount;
    }

    public void setBasedOnCount(Integer basedOnCount) {
        this.basedOnCount = basedOnCount;
    }

    public String getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(String observedAt) {
        this.observedAt = observedAt;
    }
}
