package com.aether.app.estimate;

import com.aether.app.auth.UserProfile;
import com.aether.app.auth.UserProfileRepository;
import com.aether.app.offer.Offer;
import com.aether.app.offer.OfferRepository;
import com.aether.app.project.Project;
import com.aether.app.task.Task;
import com.aether.app.task.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Injects calendar/task/assignee labor context into the JSON sent to the Tenant-Adaptive agent.
 */
@Service
public class LaborSchedulingContextService {

    public static final int STANDARD_WORKDAY_HOURS = 8;

    private final TaskRepository taskRepository;
    private final OfferRepository offerRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    public LaborSchedulingContextService(TaskRepository taskRepository,
                                         OfferRepository offerRepository,
                                         UserProfileRepository userProfileRepository,
                                         ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.offerRepository = offerRepository;
        this.userProfileRepository = userProfileRepository;
        this.objectMapper = objectMapper;
    }

    public Mono<String> mergeLaborSchedulingIntoTraining(String trainingJson,
                                                          String tenantId,
                                                          String projectId,
                                                          Project project) {
        return Mono.zip(
                taskRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList(),
                offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList(),
                userProfileRepository.findAllByTenantId(tenantId).collectList()
        ).map(tuple -> {
            List<Task> tasks = tuple.getT1();
            List<Offer> offers = tuple.getT2();
            List<UserProfile> profiles = tuple.getT3();
            Map<String, Double> projectOverrides = project.getLaborRateOverrides() != null
                    ? project.getLaborRateOverrides()
                    : Map.of();

            ArrayNode members = objectMapper.createArrayNode();
            for (UserProfile p : profiles) {
                Double def = p.getHourlyLaborRate();
                Double over = projectOverrides.get(p.getId());
                Double effective = over != null ? over : def;
                ObjectNode m = objectMapper.createObjectNode();
                m.put("userProfileId", p.getId());
                m.put("displayName", displayName(p));
                if (def != null) {
                    m.put("defaultHourlyRate", def);
                } else {
                    m.putNull("defaultHourlyRate");
                }
                if (over != null) {
                    m.put("projectOverrideHourlyRate", over);
                } else {
                    m.putNull("projectOverrideHourlyRate");
                }
                if (effective != null) {
                    m.put("effectiveHourlyRate", effective);
                } else {
                    m.putNull("effectiveHourlyRate");
                }
                members.add(m);
            }

            Map<String, List<Offer>> offersByTask = new HashMap<>();
            for (Offer o : offers) {
                offersByTask.computeIfAbsent(o.getTaskId(), k -> new ArrayList<>()).add(o);
            }

            ArrayNode taskNodes = objectMapper.createArrayNode();
            for (Task t : tasks) {
                int workDays = CalendarWorkdays.inclusiveCalendarDays(t.getStartDate(), t.getEndDate());
                ObjectNode tn = objectMapper.createObjectNode();
                tn.put("taskId", t.getId());
                tn.put("name", t.getName());
                if (t.getStartDate() != null) {
                    tn.put("startDate", t.getStartDate());
                } else {
                    tn.putNull("startDate");
                }
                if (t.getEndDate() != null) {
                    tn.put("endDate", t.getEndDate());
                } else {
                    tn.putNull("endDate");
                }
                tn.put("workDaysInclusive", workDays);
                ArrayNode tAssignees = objectMapper.createArrayNode();
                if (t.getAssigneeIds() != null) {
                    for (String id : t.getAssigneeIds()) {
                        tAssignees.add(id);
                    }
                }
                tn.set("taskAssigneeIds", tAssignees);

                ArrayNode offerNodes = objectMapper.createArrayNode();
                for (Offer o : offersByTask.getOrDefault(t.getId(), List.of())) {
                    ObjectNode on = objectMapper.createObjectNode();
                    on.put("offerId", o.getId());
                    on.put("parentTaskId", t.getId());
                    /*
                     * Calendar lives on the Task; every Offer under that task uses the same billable day count
                     * for labor scheduling (Task -> Offer).
                     */
                    on.put("taskWorkDaysInclusive", workDays);
                    on.put("name", o.getName());
                    if (o.getDescription() != null) {
                        on.put("description", o.getDescription());
                    } else {
                        on.putNull("description");
                    }
                    if (o.getUom() != null) {
                        on.put("uom", o.getUom());
                    } else {
                        on.putNull("uom");
                    }
                    ArrayNode oa = objectMapper.createArrayNode();
                    if (o.getAssigneeIds() != null) {
                        for (String id : o.getAssigneeIds()) {
                            oa.add(id);
                        }
                    }
                    on.set("offerAssigneeIds", oa);
                    /*
                     * Labor $ uses ONLY offer-level assignees. Task assignees schedule the task but do not
                     * substitute for missing offer assignees — those offers use implied labor from training.
                     */
                    LinkedHashSet<String> laborAssignees = new LinkedHashSet<>();
                    if (o.getAssigneeIds() != null && !o.getAssigneeIds().isEmpty()) {
                        laborAssignees.addAll(o.getAssigneeIds());
                    }
                    ArrayNode la = objectMapper.createArrayNode();
                    for (String id : laborAssignees) {
                        la.add(id);
                    }
                    on.set("laborAssigneeIdsForOffer", la);
                    on.put("requiresImpliedLaborFromTraining", laborAssignees.isEmpty());
                    offerNodes.add(on);
                }
                tn.set("offers", offerNodes);
                taskNodes.add(tn);
            }

            ObjectNode labor = objectMapper.createObjectNode();
            labor.put("standardWorkdayHours", STANDARD_WORKDAY_HOURS);
            labor.set("members", members);
            labor.set("tasks", taskNodes);

            try {
                JsonNode root;
                if (trainingJson == null || trainingJson.isBlank()) {
                    root = objectMapper.createObjectNode();
                } else {
                    root = objectMapper.readTree(trainingJson);
                }
                if (!root.isObject()) {
                    root = objectMapper.createObjectNode();
                }
                ((ObjectNode) root).set("laborScheduling", labor);
                return objectMapper.writeValueAsString(root);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to merge labor scheduling into training JSON", e);
            }
        });
    }

    private static String displayName(UserProfile p) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
            return p.getDisplayName();
        }
        String fn = p.getFirstName() != null ? p.getFirstName() : "";
        String ln = p.getLastName() != null ? p.getLastName() : "";
        String both = (fn + " " + ln).trim();
        if (!both.isEmpty()) {
            return both;
        }
        return p.getUsername();
    }
}
