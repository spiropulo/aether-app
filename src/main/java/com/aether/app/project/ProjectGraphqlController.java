package com.aether.app.project;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.aether.app.offer.OfferService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Controller
public class ProjectGraphqlController {

    private static final List<String> SUGGESTED_STATUSES = List.of(
            "DRAFT", "PLANNING", "IN_PROGRESS", "REVIEW", "COMPLETED", "ON_HOLD", "CANCELLED"
    );

    private final ProjectService projectService;
    private final OfferService offerService;

    public ProjectGraphqlController(ProjectService projectService, OfferService offerService) {
        this.projectService = projectService;
        this.offerService = offerService;
    }

    @SchemaMapping(typeName = "Project", field = "total")
    public Mono<Double> projectTotal(Project project) {
        return offerService.getProjectTotal(project.getTenantId(), project.getId());
    }

    @QueryMapping
    public Flux<String> suggestedProjectStatuses() {
        return Flux.fromIterable(SUGGESTED_STATUSES);
    }

    @QueryMapping
    public Mono<PagedResult<Project>> projects(@Argument String tenantId, @Argument PageInput page) {
        return projectService.getProjects(tenantId, page);
    }

    @QueryMapping
    public Mono<Project> project(@Argument String id, @Argument String tenantId) {
        return projectService.getProject(id, tenantId);
    }

    @MutationMapping
    public Mono<Project> createProject(@Argument CreateProjectInput input) {
        return projectService.createProject(input);
    }

    @MutationMapping
    public Mono<Project> updateProject(@Argument String id,
                                       @Argument String tenantId,
                                       @Argument UpdateProjectInput input) {
        return projectService.updateProject(id, tenantId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteProject(@Argument String id, @Argument String tenantId) {
        return projectService.deleteProject(id, tenantId);
    }
}
