package ch.swissqcommerce.backend.domain.auth.core.service;

import ch.swissqcommerce.backend.domain.auth.core.model.*;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase;
import ch.swissqcommerce.backend.domain.auth.port.out.AuthEventPublisherPort;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthenticationUseCase, EnrollmentUseCase {

    private static final long SESSION_TTL_HOURS = 24;

    private final UserRepositoryPort userRepositoryPort;
    private final SessionRepositoryPort sessionRepositoryPort;
    private final AuthEventPublisherPort eventPublisherPort;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Session login(
            String email, String password, String deviceFingerprint, String ipAddress) {
        Optional<UserAccount> userOpt = userRepositoryPort.findByEmail(email);
        // Always run an encoder.matches() even on lookup miss so timing doesn't
        // leak whether the email exists.
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(
                    password, "$2a$12$0000000000000000000000000000000000000000000000000000a");
            throw new IllegalArgumentException("Invalid credentials");
        }

        UserAccount user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash().getValue())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (user.getStatus() == AccountStatus.LOCKED) {
            throw new IllegalArgumentException("Account is locked");
        }

        Session session =
                Session.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(user.getId())
                        .deviceFingerprint(
                                deviceFingerprint != null
                                        ? new DeviceFingerprint(deviceFingerprint)
                                        : null)
                        .ipAddress(ipAddress != null ? new IPAddress(ipAddress) : null)
                        .expiresAt(OffsetDateTime.now().plusHours(SESSION_TTL_HOURS))
                        .active(true)
                        .build();

        return sessionRepositoryPort.save(session);
    }

    @Override
    public void logout(String sessionId) {
        sessionRepositoryPort
                .findById(sessionId)
                .ifPresent(
                        session -> {
                            session.invalidate();
                            sessionRepositoryPort.save(session);
                        });
    }

    @Override
    public boolean validateSession(String sessionId) {
        return sessionRepositoryPort.findById(sessionId).map(Session::isValid).orElse(false);
    }

    @Override
    public UserAccount register(String email, String password) {
        if (userRepositoryPort.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        String hashed = passwordEncoder.encode(password);

        UserAccount newUser =
                UserAccount.builder()
                        .id(UUID.randomUUID().toString())
                        .emailAddress(new EmailAddress(email))
                        .passwordHash(new PasswordHash(hashed))
                        .status(AccountStatus.ACTIVE)
                        .build();

        UserAccount savedUser = userRepositoryPort.save(newUser);
        eventPublisherPort.publishUserRegisteredEvent(
                savedUser.getId(), savedUser.getEmailAddress().getValue());

        return savedUser;
    }
}
