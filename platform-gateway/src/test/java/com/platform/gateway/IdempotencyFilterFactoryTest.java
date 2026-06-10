package com.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IdempotencyFilterFactoryTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private IdempotencyFilterFactory factory;
    private GatewayFilterChain chain;
    private ServerWebExchange exchange;
    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private HttpHeaders headers;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Stub default setIfAbsent to prevent NullPointerException in switchIfEmpty blocks
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(Mono.just(true));

        factory = new IdempotencyFilterFactory(redisTemplate);
        chain = mock(GatewayFilterChain.class);
        exchange = mock(ServerWebExchange.class);
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        headers = new HttpHeaders();

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getHeaders()).thenReturn(headers);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        
        // Mock bufferFactory for response writing
        when(response.bufferFactory()).thenReturn(new DefaultDataBufferFactory());

        // Stub response write methods to perform actual subscriptions to trigger mapping functions
        when(response.writeWith(any())).thenAnswer(invocation -> {
            Publisher<? extends DataBuffer> body = invocation.getArgument(0);
            return Flux.from(body).then();
        });
        when(response.writeAndFlushWith(any())).thenAnswer(invocation -> {
            Publisher<? extends Publisher<? extends DataBuffer>> body = invocation.getArgument(0);
            return Flux.from(body).flatMap(Flux::from).then();
        });
        when(response.setComplete()).thenReturn(Mono.empty());

        // Mock response status code setter and getter to maintain state
        final HttpStatusCode[] statusHolder = new HttpStatusCode[1];
        doAnswer(invocation -> {
            statusHolder[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatusCode(any());
        when(response.getStatusCode()).thenAnswer(invocation -> statusHolder[0]);
    }

    @Test
    public void testNoIdempotencyKey() {
        // GIVEN
        headers.remove("X-Idempotency-Key");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    public void testEmptyIdempotencyKey() {
        // GIVEN
        headers.add("X-Idempotency-Key", "   ");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    public void testRequestIsProcessing() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.just("PROCESSING"));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(response).writeWith(any());
    }

    @Test
    public void testReplayCachedResponseSuccess() throws IOException {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        Map<String, List<String>> cachedHeaders = new HashMap<>();
        cachedHeaders.put("Content-Type", Collections.singletonList("application/json"));
        cachedHeaders.put("X-Custom-Header", Collections.singletonList("CustomValue"));
        cachedHeaders.put("Connection", Collections.singletonList("keep-alive")); // Connection specific header to filter out
        cachedHeaders.put("Transfer-Encoding", Collections.singletonList("chunked")); // Transfer-Encoding specific header to filter out

        IdempotencyFilterFactory.CachedResponse cached = new IdempotencyFilterFactory.CachedResponse(201, cachedHeaders, "{\"data\":\"ok\"}");
        String cachedJson = new ObjectMapper().writeValueAsString(cached);

        when(valueOps.get(redisKey)).thenReturn(Mono.just(cachedJson));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        HttpHeaders respHeaders = response.getHeaders();
        assertEquals("application/json", respHeaders.getFirst("Content-Type"));
        assertEquals("CustomValue", respHeaders.getFirst("X-Custom-Header"));
        assertNull(respHeaders.getFirst("Connection"));
        assertNull(respHeaders.getFirst("Transfer-Encoding"));
        verify(response).writeWith(any());
    }

    @Test
    public void testReplayCachedResponseJsonError() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;
        String invalidJson = "{invalid-json}";

        when(valueOps.get(redisKey)).thenReturn(Mono.just(invalidJson));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(response).setComplete();
    }

    @Test
    public void testFirstRequestCachesSuccessfulResponse2xx() throws Exception {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));
        
        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);

        // Mock chain.filter to simulate a successful 200 response write
        when(chain.filter(decoratedExchange)).thenAnswer(invocation -> {
            ServerHttpResponse captured = capturedDecorator[0];
            assertNotNull(captured, "Decorator should have been captured when builder.response() was called");
            captured.setStatusCode(HttpStatus.OK);
            captured.getHeaders().add("Content-Type", "application/json");
            
            // Write some body through the decorator
            DataBuffer buffer = new DefaultDataBufferFactory().wrap("{\"response\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
            return captured.writeWith(Flux.just(buffer));
        });

        when(valueOps.set(eq(redisKey), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        
        verify(exchange).mutate();
        verify(builder).response(any());
        verify(valueOps).set(eq(redisKey), argThat(json -> json.contains("ok")), any(Duration.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void testFirstRequestConcurrentRaceOnSetIfAbsent() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        // Another thread set it first, so setIfAbsent returns false
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(false));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(response).writeWith(any());
    }

    @Test
    public void testFirstRequestServerErrorDeletesKey() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));

        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);

        when(chain.filter(decoratedExchange)).thenAnswer(invocation -> {
            ServerHttpResponse captured = capturedDecorator[0];
            assertNotNull(captured, "Decorator should have been captured when builder.response() was called");
            captured.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return Mono.empty();
        });

        when(redisTemplate.delete(redisKey)).thenReturn(Mono.just(1L));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(redisTemplate).delete(redisKey);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void testFirstRequestClientErrorDeletesKey() {
        // GIVEN
        String key = "test-key-client-err";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));

        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);

        when(chain.filter(decoratedExchange)).thenAnswer(invocation -> {
            ServerHttpResponse captured = capturedDecorator[0];
            assertNotNull(captured, "Decorator should have been captured when builder.response() was called");
            captured.setStatusCode(HttpStatus.BAD_REQUEST);
            return Mono.empty();
        });

        when(redisTemplate.delete(redisKey)).thenReturn(Mono.just(1L));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(redisTemplate).delete(redisKey);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testFirstRequestNullStatusCodeDeletesKey() {
        // GIVEN
        String key = "test-key-null-status";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));

        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);

        when(chain.filter(decoratedExchange)).thenAnswer(invocation -> {
            ServerHttpResponse captured = capturedDecorator[0];
            assertNotNull(captured, "Decorator should have been captured when builder.response() was called");
            captured.setStatusCode(null);
            return Mono.empty();
        });

        when(redisTemplate.delete(redisKey)).thenReturn(Mono.just(1L));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(redisTemplate).delete(redisKey);
        assertNull(response.getStatusCode());
    }

    @Test
    public void testFirstRequestFilterExceptionDeletesKey() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));

        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);

        when(chain.filter(decoratedExchange)).thenReturn(Mono.error(new RuntimeException("Simulated filter error")));
        when(redisTemplate.delete(redisKey)).thenReturn(Mono.just(1L));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).expectError(RuntimeException.class).verify();
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    public void testWriteAndFlushWithBodyCapture() {
        // GIVEN
        String key = "test-key-123";
        headers.add("X-Idempotency-Key", key);
        String redisKey = "idempotency:" + key;

        when(valueOps.get(redisKey)).thenReturn(Mono.empty());
        when(valueOps.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class))).thenReturn(Mono.just(true));

        ServerWebExchange.Builder builder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange decoratedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(builder);
        
        final ServerHttpResponse[] capturedDecorator = new ServerHttpResponse[1];
        when(builder.response(any(ServerHttpResponse.class))).thenAnswer(invocation -> {
            capturedDecorator[0] = invocation.getArgument(0);
            return builder;
        });
        
        when(builder.build()).thenReturn(decoratedExchange);
        
        when(chain.filter(decoratedExchange)).thenAnswer(invocation -> {
            ServerHttpResponse captured = capturedDecorator[0];
            assertNotNull(captured, "Decorator should have been captured when builder.response() was called");
            captured.setStatusCode(HttpStatus.OK);
            
            DataBuffer buffer = new DefaultDataBufferFactory().wrap("{\"status\":\"flushed\"}".getBytes(StandardCharsets.UTF_8));
            Publisher<DataBuffer> publisher = Flux.just(buffer);
            Publisher<? extends Publisher<? extends DataBuffer>> body = Flux.just(publisher);
            return captured.writeAndFlushWith(body);
        });

        when(valueOps.set(eq(redisKey), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // WHEN
        GatewayFilter filter = factory.apply(new IdempotencyFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        // THEN
        StepVerifier.create(result).verifyComplete();
        verify(valueOps).set(eq(redisKey), argThat(json -> json.contains("flushed")), any(Duration.class));
    }
}
