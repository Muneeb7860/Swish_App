package ch.swissqcommerce.backend.domain.auth.port.out;

public interface TokenServicePort {
    /**
     * Generate a signed JWT embedding the user's role as a claim. The role becomes "ROLE_<role>" in
     * Spring Security via JwtAuthenticationFilter.
     *
     * @param role uppercase role string — "CUSTOMER", "ADMIN", "RIDER", "WHOLESALER"
     */
    String generateToken(String sessionId, String userId, String role);

    boolean validateToken(String token);
}
