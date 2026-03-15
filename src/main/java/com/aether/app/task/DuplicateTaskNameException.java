package com.aether.app.task;

/**
 * Thrown when a task with the same name already exists in the project.
 */
public class DuplicateTaskNameException extends RuntimeException {

    public DuplicateTaskNameException(String projectId, String taskName) {
        super(String.format("A task named '%s' already exists in this project.", taskName));
    }
}
