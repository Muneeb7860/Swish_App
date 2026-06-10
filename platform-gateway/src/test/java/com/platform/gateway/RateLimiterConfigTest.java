package com.platform.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.when;

public class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();
    private final KeyResolver keyResolver = config.userKeyResolver();

    @Test
    public void testResolveApiKey() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-Key", "my-secret-api-key");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("apikey:my-secret-api-key")
                .verifyComplete();
    }

    @Test
    public void testResolveApiKeyEmptyFallbackToToken() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-Key", "  ");
        headers.add("Authorization", "Bearer my-jwt-token");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("token:my-jwt-token")
                .verifyComplete();
    }

    @Test
    public void testResolveAuthorizationToken() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer my-jwt-token");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("token:my-jwt-token")
                .verifyComplete();
    }

    @Test
    public void testResolveAuthorizationInvalidFallbackToRemoteAddress() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic credentials");

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 8080));

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("ip:192.168.1.1")
                .verifyComplete();
    }

    @Test
    public void testResolveRemoteAddress() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 8080));

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("ip:192.168.1.1")
                .verifyComplete();
    }

    @Test
    public void testResolveRemoteAddressFallback() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(null);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("ip:127.0.0.1")
                .verifyComplete();
    }

    @Test
    public void testResolveRemoteAddressUnresolvedFallback() {
        ServerWebExchange exchange = Mockito.mock(ServerWebExchange.class);
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        
        // Mock InetSocketAddress where getAddress() returns null
        InetSocketAddress unresolvedAddress = InetSocketAddress.createUnresolved("unresolved.host", 8080);
        when(request.getRemoteAddress()).thenReturn(unresolvedAddress);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("ip:127.0.0.1")
                .verifyComplete();
    }
}
