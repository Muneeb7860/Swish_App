package ch.swissqcommerce.backend.domain.auth.adapter.out.security;

import ch.swissqcommerce.backend.domain.auth.port.out.TokenServicePort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * HMAC-SHA-256 JWT generator/validator.
 *
 * The signing key, expiration, and claim shape must stay in lock-step with
 * {@link ch.swissqcommerce.backend.config.JwtAuthenticationFilter} — that
 * filter also reads {@code jwt.secret} and expects {@code sub} + {@code role}
 * claims, so any change here needs a matching change there.
 */
@Component
public class TokenServiceAdapter implements TokenServicePort {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final String jwtSecret;
    private final long expirationMs;
    private final SecretKey signingKey;

    public TokenServiceAdapter(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.jwtSecret = jwtSecret;
        this.expirationMs = expirationMs;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateToken(String sessionId, String userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claim("role", DEFAULT_ROLE)
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration() == null || claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
