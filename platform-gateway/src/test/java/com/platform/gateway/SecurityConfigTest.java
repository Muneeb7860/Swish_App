package com.platform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@WebFluxTest(SecurityConfig.class)
public class SecurityConfigTest {

    @Autowired
    private SecurityWebFilterChain securityWebFilterChain;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    public void testSpringSecurityFilterChain() {
        assertNotNull(securityWebFilterChain);
    }
}
