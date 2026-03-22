package com.aether.app.project;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.aether.app.offer.OfferService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class ProjectGraphqlController {

    private static final List<String> SUGGESTED_STATUSES = List.of(
            "Not Started",
            "In Progress",
            "On Hold",
            "Completed",
            "Cancelled"
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

    @SchemaMapping(typeName = "Project", field = "laborRateOverrides")
    public List<LaborRateOverride> projectLaborRateOverrides(Project project) {
        Map<String, Double> map = project.getLaborRateOverrides();
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<LaborRateOverride> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.add(new LaborRateOverride(e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    @QueryMapping
    public Flux<String> suggestedProjectStatuses() {
        return Flux.fromIterable(SUGGESTED_STATUSES);
    }

    @QueryMapping
    public Mono<PagedResult<Project>> projects(@Argument String tenantId,
                                               @Argument PageInput page,
                                               @ContextValue(name = "authUserId", required = false) String authUserId,
                                               @ContextValue(name = "authTenantId", required = false) String authTenantId,
                                               @ContextValue(name = "authRole", required = false) String authRole) {
        ProjectsAccess access = projectsAccess(authUserId, authTenantId, authRole, tenantId);
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);
        if (access == ProjectsAccess.EMPTY) {
            return Mono.just(new PagedResult<>(List.of(), 0, limit, offset));
        }
        if (access == ProjectsAccess.ASSIGNEE_ONLY) {
            return projectService.getProjectsForOfferAssignee(tenantId, page, authUserId);
        }
        return projectService.getProjects(tenantId, page);
    }

    @QueryMapping
    public Mono<Project> project(@Argument String id,
                                 @Argument String tenantId,
                                 @ContextValue(name = "authUserId", required = false) String authUserId,
                                 @ContextValue(name = "authTenantId", required = false) String authTenantId,
                                 @ContextValue(name = "authRole", required = false) String authRole) {
        ProjectsAccess access = projectsAccess(authUserId, authTenantId, authRole, tenantId);
        if (access == ProjectsAccess.EMPTY) {
            return Mono.empty();
        }
        Mono<Project> loaded = projectService.getProject(id, tenantId);
        if (access != ProjectsAccess.ASSIGNEE_ONLY) {
            return loaded;
        }
        return loaded.flatMap(p -> offerService.isUserAssigneeOnProject(tenantId, id, authUserId)
                .flatMap(ok -> Boolean.TRUE.equals(ok) ? Mono.just(p) : Mono.empty()));
    }

    private enum ProjectsAccess {
        /** No authenticated user context, or admin for this tenant — full tenant list. */
        ALL,
        /** Member/viewer for this tenant — only projects with an offer assignment. */
        ASSIGNEE_ONLY,
        /** Authenticated user querying a different tenant — no access. */
        EMPTY
    }

    private static ProjectsAccess projectsAccess(String authUserId,
                                                 String authTenantId,
                                                 String authRole,
                                                 String queryTenantId) {
        if (authUserId == null || authTenantId == null || authRole == null) {
            return ProjectsAccess.ALL;
        }
        if (!queryTenantId.equals(authTenantId)) {
            return ProjectsAccess.EMPTY;
        }
        if ("ADMIN".equals(authRole)) {
            return ProjectsAccess.ALL;
        }
        return ProjectsAccess.ASSIGNEE_ONLY;
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
