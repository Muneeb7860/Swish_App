package ch.swissqcommerce.bff.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom thread-safe reactive token-bucket GatewayFilter for edge IP rate limiting.
 * Returns 429 Too Many Requests when the limit is breached.
 */
@Component
public class RateLimiterFilter extends AbstractGatewayFilterFactory<RateLimiterFilter.Config> {

    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    public RateLimiterFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String ip = request.getRemoteAddress() != null 
                    ? request.getRemoteAddress().getAddress().getHostAddress() 
                    : "unknown-ip";

            TokenBucket bucket = ipBuckets.computeIfAbsent(ip, k -> new TokenBucket(config.getCapacity(), config.getRefillRatePerSecond()));

            if (bucket.tryConsume()) {
                return chain.filter(exchange);
            } else {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("Retry-After", "1");
                return exchange.getResponse().setComplete();
            }
        };
    }

    public static class Config {
        private int capacity = 10;
        private int refillRatePerSecond = 2;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillRatePerSecond() { return refillRatePerSecond; }
        public void setRefillRatePerSecond(int refillRatePerSecond) { this.refillRatePerSecond = refillRatePerSecond; }
    }

    private static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTimestamp;

        public TokenBucket(int capacity, int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens.get() >= 1) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long lastRefill = lastRefillTimestamp.get();
            long elapsedNs = now - lastRefill;
            if (elapsedNs > 0) {
                double elapsedSec = elapsedNs / 1_000_000_000.0;
                int tokensToAdd = (int) (elapsedSec * refillRate);
                if (tokensToAdd > 0) {
                    int currentTokens = tokens.get();
                    int newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                    tokens.set(newTokens);
                    lastRefillTimestamp.set(now);
                }
            }
        }
    }
}
