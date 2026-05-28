package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.domain.event.core.model.BaseDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class GlobalEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public GlobalEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishEvent(BaseDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
