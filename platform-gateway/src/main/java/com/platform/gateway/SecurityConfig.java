package com.platform.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Extract JWT from query param for WebSockets since JS can't send headers
        org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter tokenConverter = 
            new org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter();
        tokenConverter.setAllowUriQueryParameter(true);

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/public/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(org.springframework.security.config.Customizer.withDefaults())
                .bearerTokenConverter(tokenConverter)
            );
        return http.build();
    }
}
