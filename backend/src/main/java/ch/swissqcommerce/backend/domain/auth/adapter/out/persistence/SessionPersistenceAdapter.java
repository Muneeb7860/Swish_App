package ch.swissqcommerce.backend.domain.auth.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.auth.core.model.DeviceFingerprint;
import ch.swissqcommerce.backend.domain.auth.core.model.IPAddress;
import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionPersistenceAdapter implements SessionRepositoryPort {

    private final SessionJpaRepository jpa;

    @Override
    public Session save(Session session) {
        SessionEntity saved = jpa.save(toEntity(session));
        return toDomain(saved);
    }

    @Override
    public Optional<Session> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    private SessionEntity toEntity(Session s) {
        return SessionEntity.builder()
                .sessionId(s.getId())
                .userId(s.getUserId())
                .deviceFingerprint(
                        s.getDeviceFingerprint() != null
                                ? s.getDeviceFingerprint().getValue()
                                : null)
                .ipAddress(s.getIpAddress() != null ? s.getIpAddress().getValue() : null)
                .expiresAt(s.getExpiresAt())
                .active(s.isActive())
                .build();
    }

    private Session toDomain(SessionEntity e) {
        return Session.builder()
                .id(e.getSessionId())
                .userId(e.getUserId())
                .deviceFingerprint(
                        e.getDeviceFingerprint() != null
                                ? new DeviceFingerprint(e.getDeviceFingerprint())
                                : null)
                .ipAddress(e.getIpAddress() != null ? new IPAddress(e.getIpAddress()) : null)
                .expiresAt(e.getExpiresAt())
                .active(e.isActive())
                .build();
    }
}
