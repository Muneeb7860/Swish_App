package ch.swissqcommerce.backend.domain.event.core.model;

import java.time.OffsetDateTime;

public abstract class BaseDomainEventEntity {
    
    private final OffsetDateTime occurredAt;

    protected BaseDomainEventEntity() {
        this.occurredAt = OffsetDateTime.now();
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
