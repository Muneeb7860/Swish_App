package ch.swissqcommerce.backend.domain.auth.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SessionPersistenceAdapter implements SessionRepositoryPort {

    @Override
    public Session save(Session session) {
        return session;
    }

    @Override
    public Optional<Session> findById(String id) {
        return Optional.empty();
    }
}
