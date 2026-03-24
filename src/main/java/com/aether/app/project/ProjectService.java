package com.aether.app.project;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.aether.app.offer.OfferService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OfferService offerService;

    public ProjectService(ProjectRepository projectRepository, OfferService offerService) {
        this.projectRepository = projectRepository;
        this.offerService = offerService;
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

    /**
     * Projects in {@code tenantId} where {@code userProfileId} appears on at least one offer's assignee list.
     */
    public Mono<PagedResult<Project>> getProjectsForOfferAssignee(String tenantId, PageInput page, String userProfileId) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);
        return offerService.findDistinctProjectIdsForAssignee(tenantId, userProfileId)
                .flatMap(allowed -> {
                    if (allowed.isEmpty()) {
                        return Mono.just(new PagedResult<>(List.of(), 0, limit, offset));
                    }
                    return projectRepository.findAllByTenantId(tenantId)
                            .filter(p -> allowed.contains(p.getId()))
                            .collectList()
                            .map(list -> {
                                list.sort(Comparator
                                        .comparing(Project::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                        .reversed());
                                int total = list.size();
                                var items = list.stream().skip(offset).limit(limit).collect(Collectors.toList());
                                return new PagedResult<>(items, total, limit, offset);
                            });
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
        project.setAddressLine1(input.getAddressLine1());
        project.setAddressLine2(input.getAddressLine2());
        project.setCity(input.getCity());
        project.setState(input.getState());
        project.setPostalCode(input.getPostalCode());
        project.setCountry(input.getCountry());
        project.replaceLaborRateOverrides(toOverrideMap(input.getLaborRateOverrides()));
        project.setLaborWorkdayStart(blankToNull(input.getLaborWorkdayStart()));
        project.setLaborWorkdayEnd(blankToNull(input.getLaborWorkdayEnd()));
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
                    if (input.getAddressLine1() != null) {
                        existing.setAddressLine1(input.getAddressLine1());
                    }
                    if (input.getAddressLine2() != null) {
                        existing.setAddressLine2(input.getAddressLine2());
                    }
                    if (input.getCity() != null) {
                        existing.setCity(input.getCity());
                    }
                    if (input.getState() != null) {
                        existing.setState(input.getState());
                    }
                    if (input.getPostalCode() != null) {
                        existing.setPostalCode(input.getPostalCode());
                    }
                    if (input.getCountry() != null) {
                        existing.setCountry(input.getCountry());
                    }
                    if (input.getLaborRateOverrides() != null) {
                        existing.replaceLaborRateOverrides(toOverrideMap(input.getLaborRateOverrides()));
                    }
                    if (input.getLaborWorkdayStart() != null) {
                        existing.setLaborWorkdayStart(blankToNull(input.getLaborWorkdayStart()));
                    }
                    if (input.getLaborWorkdayEnd() != null) {
                        existing.setLaborWorkdayEnd(blankToNull(input.getLaborWorkdayEnd()));
                    }
                    existing.setUpdatedAt(Instant.now());
                    return projectRepository.save(existing);
                });
    }

    private static Map<String, Double> toOverrideMap(List<LaborRateOverrideInput> list) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> m = new LinkedHashMap<>();
        for (LaborRateOverrideInput o : list) {
            if (o.getUserProfileId() != null && !o.getUserProfileId().isBlank()
                    && o.getHourlyRate() != null && o.getHourlyRate() >= 0) {
                m.put(o.getUserProfileId().trim(), o.getHourlyRate());
            }
        }
        return m;
    }

    /** Clears {@link Project#getSourcePdfUploadId()} when it matches the deleted upload id. */
    public Mono<Void> clearSourcePdfUploadIfMatches(String projectId, String tenantId, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return Mono.empty();
        }
        return projectRepository.findByIdAndTenantId(projectId, tenantId)
                .flatMap(p -> {
                    if (!uploadId.equals(p.getSourcePdfUploadId())) {
                        return Mono.<Void>empty();
                    }
                    p.setSourcePdfUploadId(null);
                    p.setUpdatedAt(Instant.now());
                    return projectRepository.save(p).then();
                });
    }

    /** Sets the source PDF upload record id for an existing project (e.g. import-from-PDF on project detail). */
    public Mono<Project> setSourcePdfUploadId(String projectId, String tenantId, String sourcePdfUploadId) {
        return projectRepository.findByIdAndTenantId(projectId, tenantId)
                .flatMap(existing -> {
                    existing.setSourcePdfUploadId(sourcePdfUploadId);
                    existing.setUpdatedAt(Instant.now());
                    return projectRepository.save(existing);
                });
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    public Mono<Boolean> deleteProject(String id, String tenantId) {
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> projectRepository.delete(existing).thenReturn(true));
    }
}
