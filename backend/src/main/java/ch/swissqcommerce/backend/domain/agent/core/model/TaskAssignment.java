package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskAssignment {
    private final String taskId;
    private final String agentId;
    private final String payload; // Stored as JSON string
    private TaskStatus status;

    public TaskAssignment(String taskId, String agentId, String payload, TaskStatus status) {
        if (taskId == null || agentId == null || payload == null) {
            throw new IllegalArgumentException(
                    "TaskAssignment requires taskId, agentId, and payload");
        }
        this.taskId = taskId;
        this.agentId = agentId;
        this.payload = payload;
        this.status = status != null ? status : TaskStatus.QUEUED;
    }

    public void markCompleted() {
        if (this.status == TaskStatus.FAILED) {
            throw new IllegalStateException("Cannot complete a failed task");
        }
        this.status = TaskStatus.COMPLETED;
    }

    public void markFailed() {
        if (this.status == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Cannot fail a completed task");
        }
        this.status = TaskStatus.FAILED;
    }
}
