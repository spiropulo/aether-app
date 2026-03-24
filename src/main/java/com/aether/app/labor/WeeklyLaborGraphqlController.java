package com.aether.app.labor;

import com.aether.app.offer.OfferService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class WeeklyLaborGraphqlController {

    private final WeeklyLaborEfficiencyService weeklyLaborEfficiencyService;
    private final OfferService offerService;

    public WeeklyLaborGraphqlController(WeeklyLaborEfficiencyService weeklyLaborEfficiencyService,
                                        OfferService offerService) {
        this.weeklyLaborEfficiencyService = weeklyLaborEfficiencyService;
        this.offerService = offerService;
    }

    @QueryMapping
    public Mono<WeeklyLaborEfficiencyReport> weeklyLaborEfficiency(
            @Argument String projectId,
            @Argument String tenantId,
            @Argument String weekContainingDate,
            @Argument WeekStartMode weekStartMode,
            @Argument String assigneeId,
            @Argument String taskId,
            @ContextValue(name = "authUserId", required = false) String authUserId,
            @ContextValue(name = "authTenantId", required = false) String authTenantId,
            @ContextValue(name = "authRole", required = false) String authRole) {
        ProjectsAccess access = projectsAccess(authUserId, authTenantId, authRole, tenantId);
        if (access == ProjectsAccess.EMPTY) {
            return Mono.error(new IllegalArgumentException("Access denied for this tenant."));
        }
        if (access == ProjectsAccess.ASSIGNEE_ONLY) {
            return offerService.isUserAssigneeOnProject(tenantId, projectId, authUserId)
                    .flatMap(ok -> Boolean.TRUE.equals(ok)
                            ? weeklyLaborEfficiencyService.weeklyLaborEfficiency(
                            tenantId, projectId, weekContainingDate, weekStartMode, assigneeId, taskId)
                            : Mono.error(new IllegalArgumentException(
                            "You must be assigned on an offer for this project to view labor efficiency.")));
        }
        return weeklyLaborEfficiencyService.weeklyLaborEfficiency(
                tenantId, projectId, weekContainingDate, weekStartMode, assigneeId, taskId);
    }

    private enum ProjectsAccess {
        ALL,
        ASSIGNEE_ONLY,
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
}
