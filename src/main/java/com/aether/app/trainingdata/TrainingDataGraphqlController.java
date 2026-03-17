package com.aether.app.trainingdata;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.util.List;

@Controller
public class TrainingDataGraphqlController {

    private final TrainingDataService trainingDataService;

    public TrainingDataGraphqlController(TrainingDataService trainingDataService) {
        this.trainingDataService = trainingDataService;
    }

    @SchemaMapping(typeName = "TrainingData", field = "entries")
    public List<TrainingDataEntry> trainingDataEntries(TrainingData td) {
        return trainingDataService.parseEntries(td.getContent());
    }

    @QueryMapping
    public Mono<PagedResult<TrainingData>> tenantTrainingData(@Argument String tenantId,
                                                               @Argument PageInput page,
                                                               @Argument String search) {
        return trainingDataService.getTenantTrainingData(tenantId, page, search);
    }

    @QueryMapping
    public Mono<PagedResult<TrainingData>> projectTrainingData(@Argument String tenantId,
                                                                @Argument String projectId,
                                                                @Argument PageInput page,
                                                                @Argument String search) {
        return trainingDataService.getProjectTrainingData(tenantId, projectId, page, search);
    }

    @QueryMapping
    public Mono<TrainingData> trainingDataEntry(@Argument String id, @Argument String tenantId) {
        return trainingDataService.getTrainingDataEntry(id, tenantId);
    }

    @MutationMapping
    public Mono<TrainingData> createTenantTrainingData(@Argument CreateTenantTrainingDataInput input) {
        return trainingDataService.createTenantTrainingData(input);
    }

    @MutationMapping
    public Mono<TrainingData> createProjectTrainingData(@Argument CreateProjectTrainingDataInput input) {
        return trainingDataService.createProjectTrainingData(input);
    }

    @MutationMapping
    public Mono<TrainingData> updateTrainingData(@Argument String id,
                                                  @Argument String tenantId,
                                                  @Argument UpdateTrainingDataInput input) {
        return trainingDataService.updateTrainingData(id, tenantId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteTrainingData(@Argument String id, @Argument String tenantId) {
        return trainingDataService.deleteTrainingData(id, tenantId);
    }
}
