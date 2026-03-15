package com.aether.app.project;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Mono<PagedResult<Project>> getProjects(String tenantId, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return projectRepository.findAllByTenantId(tenantId)
                .collectList()
                .map(all -> {
                    int total = all.size();
                    var items = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
                    return new PagedResult<>(items, total, limit, offset);
                });
    }

    public Mono<Project> getProject(String id, String tenantId) {
        return projectRepository.findByIdAndTenantId(id, tenantId);
    }

    public Mono<Project> createProject(CreateProjectInput input) {
        Project project = new Project();
        project.setTenantId(input.getTenantId());
        project.setName(input.getName());
        project.setDescription(input.getDescription());
        project.setStartDate(input.getStartDate());
        project.setEndDate(input.getEndDate());
        project.setStatus(input.getStatus());
        project.setSourcePdfUploadId(input.getSourcePdfUploadId());
        Instant now = Instant.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        return projectRepository.save(project);
    }

    public Mono<Project> updateProject(String id, String tenantId, UpdateProjectInput input) {
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> {
                    if (input.getName() != null) {
                        existing.setName(input.getName());
                    }
                    if (input.getDescription() != null) {
                        existing.setDescription(input.getDescription());
                    }
                    if (input.getStartDate() != null) {
                        existing.setStartDate(input.getStartDate());
                    }
                    if (input.getEndDate() != null) {
                        existing.setEndDate(input.getEndDate());
                    }
                    if (input.getStatus() != null) {
                        existing.setStatus(input.getStatus());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return projectRepository.save(existing);
                });
    }

    public Mono<Boolean> deleteProject(String id, String tenantId) {
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> projectRepository.delete(existing).thenReturn(true));
    }
}
