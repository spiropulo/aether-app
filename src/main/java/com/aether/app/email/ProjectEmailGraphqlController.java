package com.aether.app.email;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class ProjectEmailGraphqlController {

    private final ProjectEmailService projectEmailService;

    public ProjectEmailGraphqlController(ProjectEmailService projectEmailService) {
        this.projectEmailService = projectEmailService;
    }

    @QueryMapping
    public Flux<ProjectEmail> projectEmails(@Argument String projectId, @Argument String tenantId) {
        return projectEmailService.getEmailHistory(projectId, tenantId);
    }

    @MutationMapping
    public Mono<ProjectEmail> sendProjectEmail(@Argument SendProjectEmailInput input) {
        return projectEmailService.sendEmail(
                input.getTenantId(),
                input.getProjectId(),
                input.getTaskId(),
                input.getSenderId(),
                input.getToEmails(),
                input.getSubject(),
                input.getBody()
        );
    }
}
