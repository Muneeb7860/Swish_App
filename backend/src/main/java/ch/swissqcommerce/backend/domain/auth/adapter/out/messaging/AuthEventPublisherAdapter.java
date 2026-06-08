package ch.swissqcommerce.backend.domain.auth.adapter.out.messaging;

import ch.swissqcommerce.backend.domain.auth.port.out.AuthEventPublisherPort;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisherAdapter implements AuthEventPublisherPort {

    @Override
    public void publishUserRegisteredEvent(String userId, String email) {
        // Dummy implementation for tests
    }
}
