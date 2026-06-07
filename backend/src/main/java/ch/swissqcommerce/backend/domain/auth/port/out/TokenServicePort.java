package ch.swissqcommerce.backend.domain.auth.port.out;

public interface TokenServicePort {
    String generateToken(String sessionId, String userId);
    boolean validateToken(String token);
}
