package ch.swissqcommerce.backend.domain.event.core.model;

import ch.swissqcommerce.backend.model.OutboxEvent;
import lombok.Getter;

@Getter
public class OutboxEventSavedEvent {
    private final OutboxEvent event;

    public OutboxEventSavedEvent(OutboxEvent event) {
        this.event = event;
    }
}
