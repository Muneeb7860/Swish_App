package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.domain.event.port.out.EventPort;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventUseCase {

    private final EventPort eventPort;

    @Override
    public DomainEventEntity publishEvent(String topic, String payload) {
        DomainEventEntity event =
                DomainEventEntity.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .eventType(topic)
                        .payload(payload)
                        .status("PENDING")
                        .createdAt(OffsetDateTime.now())
                        .build();

        return eventPort.saveEvent(event);
    }
}
