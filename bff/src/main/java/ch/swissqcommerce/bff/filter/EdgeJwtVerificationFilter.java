package ch.swissqcommerce.bff.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Edge JWT verification filter.
 * Parses the incoming Authorization Bearer token OR secure HttpOnly cookie (mitigating XSS theft),
 * validates the cryptographic signature, strips incoming spoof headers (X-User-Subject, X-User-Roles),
 * and injects verified security credentials as downstream headers.
 */
@Component
public class EdgeJwtVerificationFilter extends AbstractGatewayFilterFactory<EdgeJwtVerificationFilter.Config> {

    @Value("${jwt.secret:9a4f2c5e8b1d3a7c6f0e2b4d6a8c0e2f4a6b8d0c2e4f6a8b0c2d4e6f8a0b2c4d}")
    private String jwtSecret;

    public EdgeJwtVerificationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Strip incoming sensitive headers from external clients to prevent spoofing
            ServerHttpRequest.Builder requestBuilder = request.mutate();
            requestBuilder.headers(headers -> {
                headers.remove("X-User-Subject");
                headers.remove("X-User-Roles");
            });

            String path = request.getURI().getPath();
            // Bypass security verification on OPTIONS preflight requests, public auth endpoints, and Swagger UI/OpenAPI docs
            if (request.getMethod().name().equals("OPTIONS") 
                    || path.contains("/api/auth/login") 
                    || path.contains("/api/auth/mfa/verify")
                    || path.contains("/v3/api-docs")
                    || path.contains("/swagger-ui")) {
                return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
            }

            String token = null;
            String authHeader = request.getHeaders().getFirst("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                // Secure HttpOnly Cookie fallback to prevent script-based XSS theft
                HttpCookie cookie = request.getCookies().getFirst("jwt_session");
                if (cookie != null) {
                    token = cookie.getValue();
                }
            }

            if (token != null) {
                try {
                    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    String subject = claims.getSubject();
                    String roles = claims.get("roles", String.class);

                    if (subject != null) {
                        requestBuilder.header("X-User-Subject", subject);
                        if (roles != null) {
                            requestBuilder.header("X-User-Roles", roles);
                        }
                        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                    }
                } catch (Exception e) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }

            // Unauthorized if no valid token exists in headers or cookies
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        };
    }

    public static class Config {
        // Add config properties if required
    }
}
