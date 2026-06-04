package ch.swissqcommerce.bff.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Custom thread-safe reactive Redis-backed token-bucket GatewayFilter for edge IP/user rate limiting.
 * Falls back gracefully to passive-allow if the Redis cluster is unreachable.
 * Returns 429 Too Many Requests when the limit is breached.
 */
@Component
public class RateLimiterFilter extends AbstractGatewayFilterFactory<RateLimiterFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);

    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local rate = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local ttl = 3600\n" +
            "\n" +
            "local last_tokens = tonumber(redis.call('get', key .. ':tokens'))\n" +
            "if last_tokens == nil then\n" +
            "  last_tokens = limit\n" +
            "end\n" +
            "\n" +
            "local last_refreshed = tonumber(redis.call('get', key .. ':ts'))\n" +
            "if last_refreshed == nil then\n" +
            "  last_refreshed = 0\n" +
            "end\n" +
            "\n" +
            "local delta = math.max(0, now - last_refreshed)\n" +
            "local tokens = math.min(limit, last_tokens + (delta * rate))\n" +
            "\n" +
            "local allowed = tokens >= 1\n" +
            "local allowed_num = 0\n" +
            "if allowed then\n" +
            "  tokens = tokens - 1\n" +
            "  allowed_num = 1\n" +
            "end\n" +
            "\n" +
            "redis.call('setex', key .. ':tokens', ttl, tokens)\n" +
            "redis.call('setex', key .. ':ts', ttl, now)\n" +
            "\n" +
            "return allowed_num";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> redisScript;

    @Autowired
    public RateLimiterFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);
        this.redisScript = script;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Limit by authenticated user subject first to prevent NAT/IP sharing collision, fallback to IP
            String limitKey = request.getHeaders().getFirst("X-User-Subject");
            if (limitKey == null || limitKey.trim().isEmpty()) {
                limitKey = request.getRemoteAddress() != null
                        ? request.getRemoteAddress().getAddress().getHostAddress()
                        : "unknown-ip";
            }

            String finalLimitKey = limitKey;
            List<String> keys = List.of("rl:" + finalLimitKey);
            List<String> args = List.of(
                    String.valueOf(config.getCapacity()),
                    String.valueOf(config.getRefillRatePerSecond()),
                    String.valueOf(System.currentTimeMillis() / 1000L)
            );

            return redisTemplate.execute(redisScript, keys, args)
                    .next()
                    .map(allowed -> allowed == 1L)
                    .onErrorResume(err -> {
                        log.warn("⚠️ [RateLimiterFilter] Redis execution failed: {}. Falling back to passive-allow.", err.getMessage());
                        return Mono.just(true);
                    })
                    .flatMap(allowed -> {
                        if (allowed) {
                            log.debug("✅ [RateLimiterFilter] Allowed request for: {}", finalLimitKey);
                            return chain.filter(exchange);
                        } else {
                            log.warn("❌ [RateLimiterFilter] Rate limited request for: {}", finalLimitKey);
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            exchange.getResponse().getHeaders().add("Retry-After", "1");
                            return exchange.getResponse().setComplete();
                        }
                    });
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
}
