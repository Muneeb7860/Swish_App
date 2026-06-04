package com.platform.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class IdempotencyFilterFactory extends AbstractGatewayFilterFactory<IdempotencyFilterFactory.Config> {

    private final ReactiveStringRedisTemplate redisTemplate;

    public IdempotencyFilterFactory(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String idempotencyKey = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");
            
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                // If no key is provided, just pass through (or you could reject it)
                return chain.filter(exchange);
            }

            String redisKey = "idempotency:" + idempotencyKey;

            return redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24))
                    .flatMap(isNew -> {
                        if (Boolean.TRUE.equals(isNew)) {
                            // First time seeing this key, proceed
                            return chain.filter(exchange).then(Mono.defer(() -> {
                                HttpStatus statusCode = exchange.getResponse().getStatusCode();
                                if (statusCode != null && statusCode.is5xxServerError()) {
                                    // Backend failed, delete the key so client can retry
                                    return redisTemplate.delete(redisKey).then();
                                } else if (statusCode != null && statusCode.is2xxSuccessful()) {
                                    // Success, mark as completed
                                    return redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofHours(24)).then();
                                }
                                return Mono.empty();
                            })).onErrorResume(throwable -> {
                                // Network timeout or severe error, delete the key
                                return redisTemplate.delete(redisKey).then(Mono.error(throwable));
                            });
                        } else {
                            // Duplicate request detected, block it
                            exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
                            return exchange.getResponse().setComplete();
                        }
                    });
        };
    }

    public static class Config {
        // Configuration properties can go here
    }
}
