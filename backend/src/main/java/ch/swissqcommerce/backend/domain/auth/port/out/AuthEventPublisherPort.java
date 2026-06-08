package ch.swissqcommerce.backend.domain.auth.port.out;

public interface AuthEventPublisherPort {
    void publishUserRegisteredEvent(String userId, String email);
}
