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

    public String getEventType() {
        return this.getClass().getSimpleName();
    }

    public String getAggregateId() {
        return "";
    }

    public String getPayload() {
        return "";
    }
}
