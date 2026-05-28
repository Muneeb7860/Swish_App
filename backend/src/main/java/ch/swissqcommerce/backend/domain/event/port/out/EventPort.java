package ch.swissqcommerce.backend.domain.event.port.out;

import ch.swissqcommerce.backend.domain.event.core.model.DomainEvent;

public interface EventPort {
    DomainEvent saveEvent(DomainEvent event);
}
