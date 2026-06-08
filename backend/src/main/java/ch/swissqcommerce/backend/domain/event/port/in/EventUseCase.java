package ch.swissqcommerce.backend.domain.event.port.in;

import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;

public interface EventUseCase {
    DomainEventEntity publishEvent(String topic, String payload);
}
