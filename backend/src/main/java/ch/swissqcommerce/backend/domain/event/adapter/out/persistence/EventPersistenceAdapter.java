package ch.swissqcommerce.backend.domain.event.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.event.core.model.DomainEvent;
import ch.swissqcommerce.backend.domain.event.port.out.EventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventPersistenceAdapter implements EventPort {

    private final DomainEventRepository repository;

    @Override
    public DomainEvent saveEvent(DomainEvent event) {
        return repository.save(event);
    }
}
