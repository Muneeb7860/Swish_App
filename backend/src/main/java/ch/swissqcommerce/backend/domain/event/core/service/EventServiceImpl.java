package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.domain.event.core.model.DomainEvent;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.domain.event.port.out.EventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventUseCase {

    private final EventPort eventPort;

    @Override
    public DomainEvent publishEvent(String topic, String payload) {
        DomainEvent event = DomainEvent.builder()
                .topic(topic)
                .payload(payload)
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();
        
        return eventPort.saveEvent(event);
    }
}
