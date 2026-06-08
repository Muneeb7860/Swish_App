package ch.swissqcommerce.backend.domain.auth.port.in;

import ch.swissqcommerce.backend.domain.auth.core.model.Session;

public interface AuthenticationUseCase {
    Session login(String email, String password, String deviceFingerprint, String ipAddress);
    void logout(String sessionId);
    boolean validateSession(String sessionId);
}
