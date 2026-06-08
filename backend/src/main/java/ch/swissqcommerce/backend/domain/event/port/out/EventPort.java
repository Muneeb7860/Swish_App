package ch.swissqcommerce.backend.domain.event.port.out;

import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;

public interface EventPort {
    DomainEventEntity saveEvent(DomainEventEntity event);
}
