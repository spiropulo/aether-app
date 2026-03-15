package com.aether.app.task;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class TaskGraphqlController {

    private final TaskService taskService;

    public TaskGraphqlController(TaskService taskService) {
        this.taskService = taskService;
    }

    @QueryMapping
    public Mono<PagedResult<Task>> tasks(@Argument String tenantId,
                                         @Argument String projectId,
                                         @Argument PageInput page) {
        return taskService.getTasks(tenantId, projectId, page);
    }

    @QueryMapping
    public Mono<Task> task(@Argument String id,
                           @Argument String tenantId,
                           @Argument String projectId) {
        return taskService.getTask(id, tenantId, projectId);
    }

    @MutationMapping
    public Mono<Task> createTask(@Argument CreateTaskInput input) {
        return taskService.createTask(input);
    }

    @MutationMapping
    public Mono<Task> updateTask(@Argument String id,
                                  @Argument String tenantId,
                                  @Argument String projectId,
                                  @Argument UpdateTaskInput input) {
        return taskService.updateTask(id, tenantId, projectId, input);
    }

    @MutationMapping
    public Mono<Boolean> deleteTask(@Argument String id,
                                     @Argument String tenantId,
                                     @Argument String projectId) {
        return taskService.deleteTask(id, tenantId, projectId);
    }
}
