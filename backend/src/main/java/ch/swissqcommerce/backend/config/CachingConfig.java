package ch.swissqcommerce.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * Enables caching for every environment and makes the cache layer fault-tolerant.
 *
 * <p>The cache is an optimization, never a dependency: if the backing store (Redis in production)
 * is unreachable, a lookup must degrade to a cache miss and a write must be skipped — the
 * underlying read path still serves the request from the database. Without this handler a Redis
 * outage turns every {@code @Cacheable} read into a 500.
 */
@Configuration
@EnableCaching
public class CachingConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CachingConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(
                    @NonNull RuntimeException e, @NonNull Cache cache, @NonNull Object key) {
                log.warn(
                        "Cache GET failed on '{}' (treating as miss): {}",
                        cache.getName(),
                        e.getMessage());
            }

            @Override
            public void handleCachePutError(
                    @NonNull RuntimeException e,
                    @NonNull Cache cache,
                    @NonNull Object key,
                    Object value) {
                log.warn("Cache PUT failed on '{}' (skipped): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(
                    @NonNull RuntimeException e, @NonNull Cache cache, @NonNull Object key) {
                log.warn(
                        "Cache EVICT failed on '{}' (skipped): {}",
                        cache.getName(),
                        e.getMessage());
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException e, @NonNull Cache cache) {
                log.warn(
                        "Cache CLEAR failed on '{}' (skipped): {}",
                        cache.getName(),
                        e.getMessage());
            }
        };
    }
}
