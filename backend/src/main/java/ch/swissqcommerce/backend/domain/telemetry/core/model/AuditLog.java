package ch.swissqcommerce.backend.domain.telemetry.core.model;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
public class AuditLog {
    private final String logId;
    private final String aggregateId;
    private final String actionType;
    private final String actorId;
    private final String payloadSnapshot; // Stored as JSON string
    private final Instant createdAt;

    public AuditLog(String logId, String aggregateId, String actionType, String actorId, String payloadSnapshot, Instant createdAt) {
        if (logId == null || aggregateId == null || actionType == null) {
            throw new IllegalArgumentException("AuditLog requires logId, aggregateId, and actionType");
        }
        this.logId = logId;
        this.aggregateId = aggregateId;
        this.actionType = actionType;
        this.actorId = actorId;
        this.payloadSnapshot = payloadSnapshot;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
