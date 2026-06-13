package com.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-Grade Idempotency Gateway Filter.
 * Resolves race conditions on concurrent duplicate requests using lock token,
 * caches downstream response details (status, headers, and body),
 * and replays cached responses on identical idempotency keys.
 */
@Component
public class IdempotencyGatewayFilterFactory extends AbstractGatewayFilterFactory<IdempotencyGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGatewayFilterFactory.class);
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdempotencyGatewayFilterFactory(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String idempotencyKey = request.getHeaders().getFirst("X-Idempotency-Key");

            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return chain.filter(exchange);
            }

            String redisKey = "idempotency:" + idempotencyKey;
            return redisTemplate.opsForValue().get(redisKey)
                    .flatMap(state -> {
                        if ("PROCESSING".equals(state)) {
                            // Request is currently running. Return 409 Conflict.
                            return handleConflict(exchange, idempotencyKey).then(Mono.just(true));
                        } else {
                            // Request is completed and response is cached. Replay it.
                            return handleReplay(exchange, state).then(Mono.just(true));
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // Key does not exist, mark as PROCESSING
                        return redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24))
                                .flatMap(success -> {
                                    if (Boolean.TRUE.equals(success)) {
                                        // Decorated response to capture body
                                        ResponseCaptureDecorator decorator = new ResponseCaptureDecorator(exchange.getResponse());
                                        ServerWebExchange decoratedExchange = exchange.mutate().response(decorator).build();

                                        return chain.filter(decoratedExchange)
                                                .then(Mono.defer(() -> {
                                                    HttpStatusCode statusCode = decorator.getStatusCode();
                                                    if (statusCode != null && (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection())) {
                                                        return cacheResponse(redisKey, decorator);
                                                    } else {
                                                        return redisTemplate.delete(redisKey).then();
                                                    }
                                                }))
                                                .onErrorResume(throwable -> {
                                                    // Severe network error/exception, delete key
                                                    log.error("Error occurred during idempotency request execution. Deleting key {}: {}", redisKey, throwable.getMessage());
                                                    return redisTemplate.delete(redisKey).then(Mono.error(throwable));
                                                });
                                    } else {
                                        // Concurrent race condition, key was set right after our check
                                        return handleConflict(exchange, idempotencyKey);
                                    }
                                }).then(Mono.just(true));
                    }))
                    .then();
        };
    }

    private Mono<Void> handleConflict(ServerWebExchange exchange, String key) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = String.format("{\"error\":\"Idempotency Conflict\",\"message\":\"A request with key '%s' is already in progress. Please retry shortly.\"}", key);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> handleReplay(ServerWebExchange exchange, String cachedJson) {
        try {
            CachedResponse cached = objectMapper.readValue(cachedJson, CachedResponse.class);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.valueOf(cached.getStatus()));

            // Replay headers, filtering out connection specific headers
            cached.getHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Transfer-Encoding") && !name.equalsIgnoreCase("Connection")) {
                    response.getHeaders().put(name, values);
                }
            });

            byte[] bytes = cached.getBody().getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (IOException e) {
            log.error("Failed to parse cached response: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> cacheResponse(String redisKey, ResponseCaptureDecorator decorator) {
        HttpStatusCode status = decorator.getStatusCode();
        int statusCode = status != null ? status.value() : 200;

        Map<String, List<String>> headers = new HashMap<>(decorator.getHeaders());
        String body = decorator.getCapturedBodyAsString();

        CachedResponse cached = new CachedResponse(statusCode, headers, body);
        try {
            String json = objectMapper.writeValueAsString(cached);
            return redisTemplate.opsForValue().set(redisKey, json, Duration.ofHours(24)).then();
        } catch (Exception e) {
            log.error("Failed to serialize response for caching: {}", e.getMessage());
            return Mono.empty();
        }
    }

    public static class CachedResponse {
        private int status;
        private Map<String, List<String>> headers;
        private String body;

        public CachedResponse() {}

        public CachedResponse(int status, Map<String, List<String>> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public Map<String, List<String>> getHeaders() { return headers; }
        public void setHeaders(Map<String, List<String>> headers) { this.headers = headers; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    private static class ResponseCaptureDecorator extends ServerHttpResponseDecorator {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public ResponseCaptureDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).map(buffer -> {
                // Duplicate buffer slice to read without exhausting original stream
                DataBuffer slice = buffer.slice(0, buffer.readableByteCount());
                byte[] bytes = new byte[slice.readableByteCount()];
                slice.read(bytes);
                baos.write(bytes, 0, bytes.length);
                return buffer;
            }));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return super.writeAndFlushWith(Flux.from(body).map(publisher ->
                    Flux.from(publisher).map(buffer -> {
                        DataBuffer slice = buffer.slice(0, buffer.readableByteCount());
                        byte[] bytes = new byte[slice.readableByteCount()];
                        slice.read(bytes);
                        baos.write(bytes, 0, bytes.length);
                        return buffer;
                    })
            ));
        }

        public String getCapturedBodyAsString() {
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static class Config {
        // Configuration properties can go here
    }
}
