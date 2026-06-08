package ch.swissqcommerce.backend.domain.auth.port.out;

import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import java.util.Optional;

public interface SessionRepositoryPort {
    Session save(Session session);
    Optional<Session> findById(String id);
}
