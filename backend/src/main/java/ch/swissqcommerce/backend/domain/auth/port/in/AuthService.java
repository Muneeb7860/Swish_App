package ch.swissqcommerce.backend.domain.auth.port.in;

import ch.swissqcommerce.backend.domain.auth.core.model.LoginResponse;

public interface AuthService {
    LoginResponse login(String username, String password);
    String verifyMfa(String sessionToken, String code);
}
