package com.platform.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${jwt.secret:my-secret-key-that-is-long-enough-to-be-secure-for-jwt-signature-verification-32bytes-long}")
    private String jwtSecret;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] secretKeyBytes = jwtSecret.getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Extract JWT from query param for WebSockets since JS can't send headers
        org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter tokenConverter = 
            new org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter();
        tokenConverter.setAllowUriQueryParameter(true);

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/api/auth/**", "/api/v1/auth/**").permitAll()
                .pathMatchers("/api/v1/retailers/register").permitAll()
                .pathMatchers("/api/v1/sensors/readings").permitAll()
                .pathMatchers("/api/v1/public/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .pathMatchers("/error").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(org.springframework.security.config.Customizer.withDefaults())
                .bearerTokenConverter(tokenConverter)
            );
        return http.build();
    }
}
