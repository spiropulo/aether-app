package com.aether.app.email;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * Record of a project notification (email and/or SMS). Stored for history/audit.
 */
@Document(collectionName = "projectEmails")
public class ProjectEmail {

    @DocumentId
    private String id;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String projectId;

    /** Optional: task context for this send. */
    private String taskId;

    /** Optional: when set, send was scoped to this offer's assignees in the UI. */
    private String offerId;

    /** User profile ID of the sender. */
    private String senderId;

    /** Recipient email addresses. */
    private List<String> toEmails;

    /** Recipient phone numbers (E.164) when SMS was used. */
    private List<String> toPhoneNumbers;

    /** e.g. EMAIL, SMS — order reflects what was sent. */
    private List<String> deliveryChannels;

    private String subject;
    private String body;

    private Instant sentAt;

    public ProjectEmail() {
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public List<String> getToEmails() {
        return toEmails != null ? toEmails : List.of();
    }

    public void setToEmails(List<String> toEmails) {
        this.toEmails = toEmails;
    }

    public List<String> getToPhoneNumbers() {
        return toPhoneNumbers != null ? toPhoneNumbers : List.of();
    }

    public void setToPhoneNumbers(List<String> toPhoneNumbers) {
        this.toPhoneNumbers = toPhoneNumbers;
    }

    public List<String> getDeliveryChannels() {
        if (deliveryChannels != null && !deliveryChannels.isEmpty()) {
            return deliveryChannels;
        }
        return List.of("EMAIL");
    }

    public void setDeliveryChannels(List<String> deliveryChannels) {
        this.deliveryChannels = deliveryChannels;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
