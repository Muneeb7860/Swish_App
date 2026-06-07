package ch.swissqcommerce.backend.domain.auth.core.service;

import ch.swissqcommerce.backend.domain.auth.core.model.*;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase;
import ch.swissqcommerce.backend.domain.auth.port.out.AuthEventPublisherPort;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthenticationUseCase, EnrollmentUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final SessionRepositoryPort sessionRepositoryPort;
    private final AuthEventPublisherPort eventPublisherPort;

    @Override
    public Session login(String email, String password, String deviceFingerprint, String ipAddress) {
        Optional<UserAccount> userOpt = userRepositoryPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        UserAccount user = userOpt.get();
        // In a real app, use a password encoder to verify
        if (!user.getPasswordHash().getValue().equals(password)) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        Session session = Session.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.getId())
                .deviceFingerprint(new DeviceFingerprint(deviceFingerprint))
                .ipAddress(new IPAddress(ipAddress))
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .active(true)
                .build();

        return sessionRepositoryPort.save(session);
    }

    @Override
    public void logout(String sessionId) {
        sessionRepositoryPort.findById(sessionId).ifPresent(session -> {
            session.invalidate();
            sessionRepositoryPort.save(session);
        });
    }

    @Override
    public boolean validateSession(String sessionId) {
        return sessionRepositoryPort.findById(sessionId)
                .map(Session::isValid)
                .orElse(false);
    }

    @Override
    public UserAccount register(String email, String password) {
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        UserAccount newUser = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .emailAddress(new EmailAddress(email))
                .passwordHash(new PasswordHash(password)) // Should be hashed!
                .status(AccountStatus.ACTIVE)
                .build();

        UserAccount savedUser = userRepositoryPort.save(newUser);
        eventPublisherPort.publishUserRegisteredEvent(savedUser.getId(), savedUser.getEmailAddress().getValue());
        
        return savedUser;
    }
}
