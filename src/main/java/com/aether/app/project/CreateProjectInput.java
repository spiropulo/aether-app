package com.aether.app.project;

import java.util.List;

public class CreateProjectInput {

    private String tenantId;
    private String name;
    private String description;
    private String startDate;
    private String endDate;
    private String status;
    private String sourcePdfUploadId;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private List<LaborRateOverrideInput> laborRateOverrides;
    private String laborWorkdayStart;
    private String laborWorkdayEnd;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourcePdfUploadId() {
        return sourcePdfUploadId;
    }

    public void setSourcePdfUploadId(String sourcePdfUploadId) {
        this.sourcePdfUploadId = sourcePdfUploadId;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public List<LaborRateOverrideInput> getLaborRateOverrides() {
        return laborRateOverrides;
    }

    public void setLaborRateOverrides(List<LaborRateOverrideInput> laborRateOverrides) {
        this.laborRateOverrides = laborRateOverrides;
    }

    public String getLaborWorkdayStart() {
        return laborWorkdayStart;
    }

    public void setLaborWorkdayStart(String laborWorkdayStart) {
        this.laborWorkdayStart = laborWorkdayStart;
    }

    public String getLaborWorkdayEnd() {
        return laborWorkdayEnd;
    }

    public void setLaborWorkdayEnd(String laborWorkdayEnd) {
        this.laborWorkdayEnd = laborWorkdayEnd;
    }
}
