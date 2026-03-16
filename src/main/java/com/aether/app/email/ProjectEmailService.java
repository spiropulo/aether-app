package com.aether.app.email;

import com.aether.app.mail.EmailService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectEmailService {

    private final ProjectEmailRepository emailRepository;
    private final EmailService emailService;

    public ProjectEmailService(ProjectEmailRepository emailRepository, EmailService emailService) {
        this.emailRepository = emailRepository;
        this.emailService = emailService;
    }

    /**
     * Send email to project assignees (all tasks) or task assignees only.
     * Persists the record and sends via SMTP.
     */
    public Mono<ProjectEmail> sendEmail(String tenantId, String projectId, String taskId,
                                       String senderId, List<String> toEmails, String subject, String body) {
        if (toEmails == null || toEmails.isEmpty()) {
            return Mono.error(new IllegalArgumentException("At least one recipient is required."));
        }
        if (subject == null || subject.isBlank()) {
            return Mono.error(new IllegalArgumentException("Subject is required."));
        }

        ProjectEmail record = new ProjectEmail();
        record.setId(UUID.randomUUID().toString());
        record.setTenantId(tenantId);
        record.setProjectId(projectId);
        record.setTaskId(taskId);
        record.setSenderId(senderId);
        record.setToEmails(toEmails);
        record.setSubject(subject);
        record.setBody(body != null ? body : "");
        record.setSentAt(Instant.now());

        return emailService.send(toEmails.toArray(new String[0]), subject, body != null ? body : "")
                .then(emailRepository.save(record))
                .thenReturn(record);
    }

    public Flux<ProjectEmail> getEmailHistory(String projectId, String tenantId) {
        return emailRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .sort((a, b) -> {
                    Instant sa = a.getSentAt();
                    Instant sb = b.getSentAt();
                    if (sa == null && sb == null) return 0;
                    if (sa == null) return 1;
                    if (sb == null) return -1;
                    return sb.compareTo(sa); // newest first
                });
    }
}
