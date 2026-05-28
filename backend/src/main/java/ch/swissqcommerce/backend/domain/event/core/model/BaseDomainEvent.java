package ch.swissqcommerce.backend.domain.event.core.model;

import java.time.OffsetDateTime;

public abstract class BaseDomainEvent {
    
    private final OffsetDateTime occurredAt;

    protected BaseDomainEvent() {
        this.occurredAt = OffsetDateTime.now();
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
