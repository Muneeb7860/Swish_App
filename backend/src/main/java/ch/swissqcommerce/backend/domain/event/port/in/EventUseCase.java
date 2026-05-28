package ch.swissqcommerce.backend.domain.event.port.in;

import ch.swissqcommerce.backend.domain.event.core.model.DomainEvent;

public interface EventUseCase {
    DomainEvent publishEvent(String topic, String payload);
}
