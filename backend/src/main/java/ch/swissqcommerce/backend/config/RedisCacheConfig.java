package ch.swissqcommerce.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis cache manager with per-cache TTL policies and JSON serialization.
 *
 * <p>Cache names and their TTLs: orders — 5 min (mutable; evicted on delivery/refund)
 * customer-orders — 2 min (list changes on new order) wholesaler-restocks — 5 min
 * wholesaler-invoices — 10 min (financial summary, infrequent mutations) academy-courses — 60 min
 * (static reference data) system-health — 30 sec (dashboard poll; stale-ok) catalog — 60 min
 * (product catalog, rarely changes)
 *
 * <p>Active only when {@code spring.cache.type} is {@code redis} or unset (production default).
 * Profiles that set {@code spring.cache.type=simple} (dev/E2E, no Redis available) fall back to
 * Spring Boot's auto-configured in-memory cache manager instead. {@code @EnableCaching} itself
 * lives in {@link CachingConfig} so cache annotations stay active in every environment.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /** Shared Jackson serializer that stores type information alongside the value. */
    @Bean
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.registerModule(
                new com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule());
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer jsonSerializer) {
        RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        jsonSerializer))
                        .prefixCacheNameWith("swish:")
                        .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCacheTtl =
                Map.of(
                        "orders", base.entryTtl(Duration.ofMinutes(5)),
                        "customer-orders", base.entryTtl(Duration.ofMinutes(2)),
                        "wholesaler-restocks", base.entryTtl(Duration.ofMinutes(5)),
                        "wholesaler-invoices", base.entryTtl(Duration.ofMinutes(10)),
                        "academy-courses", base.entryTtl(Duration.ofMinutes(60)),
                        "system-health", base.entryTtl(Duration.ofSeconds(30)),
                        "catalog", base.entryTtl(Duration.ofMinutes(60)),
                        "carrier-rates", base.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(perCacheTtl)
                .build();
    }
}
