package ch.swissqcommerce.backend.domain.auth.adapter.out.security;

import ch.swissqcommerce.backend.domain.auth.port.out.TokenServicePort;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class TokenServiceAdapter implements TokenServicePort {

    @Override
    public String generateToken(String sessionId, String userId) {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean validateToken(String token) {
        return true;
    }
}
