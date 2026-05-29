package ch.swissqcommerce.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Blueprint: Hybrid Redis Geospatial Store with In-Memory Fallback.
 *
 * <p>Primary path: Spring Data Redis {@code GEOADD} / {@code GEOPOS} commands via
 * {@link StringRedisTemplate}. The Redis key pattern is {@code geo:rider:{orderId}}.
 *
 * <p>Fallback path: If Redis is unavailable (connection refused, circuit-breaker open,
 * or chaos injection active) the component transparently degrades to a
 * {@link ConcurrentHashMap} in-memory store — ensuring zero impact on checkout throughput.
 *
 * <p>Temperature is stored as a hash-field alongside the geospatial entry because
 * Redis GEOPOS only tracks lat/lng. A secondary {@code temp:{orderId}} String key holds
 * the temperature value serialised as a plain decimal string.
 */
@Component
public class InMemoryGeoStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryGeoStore.class);

    /**
     * Redis Geo key prefix: {@code geo:rider:{orderId}}
     * Member name is the string form of orderId so GEOPOS can retrieve a single point.
     */
    private static final String GEO_KEY_PREFIX  = "geo:rider:";
    private static final String TEMP_KEY_PREFIX = "temp:rider:";

    // ── Fallback in-memory store ──────────────────────────────────────────────
    private final ConcurrentHashMap<Integer, RiderLocation> fallbackStore = new ConcurrentHashMap<>();

    // ── Spring Data Redis ─────────────────────────────────────────────────────
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public InMemoryGeoStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Persist rider geospatial coordinates and temperature for the given order.
     *
     * <p>Attempts {@code GEOADD geo:rider:{orderId} lng lat "{orderId}"} via Redis.
     * Falls back to the in-memory map on any exception.
     */
    public void updateLocation(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        // Always update in-memory fallback for zero-latency local reads
        fallbackStore.put(orderId, new RiderLocation(lat, lng, temp));

        try {
            GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
            String geoKey  = GEO_KEY_PREFIX  + orderId;
            String tempKey = TEMP_KEY_PREFIX + orderId;
            String member  = String.valueOf(orderId);

            // GEOADD expects (lng, lat) — note Point(x=lng, y=lat)
            geoOps.add(geoKey, new Point(lng.doubleValue(), lat.doubleValue()), member);

            // Store temperature as a plain string value (TTL: 3600 s = 1 hr)
            redisTemplate.opsForValue().set(tempKey, temp.toPlainString());
            redisTemplate.expire(geoKey,  java.time.Duration.ofHours(1));
            redisTemplate.expire(tempKey, java.time.Duration.ofHours(1));

            log.debug("[GeoStore] Redis GEOADD orderId={} lat={} lng={} temp={}", orderId, lat, lng, temp);

        } catch (Exception ex) {
            // Redis unavailable — fallback already updated above
            log.warn("[GeoStore] Redis write failed for orderId={}. Using in-memory fallback. Cause: {}", orderId, ex.getMessage());
        }
    }

    /**
     * Retrieve the latest known rider location for the given order.
     *
     * <p>Attempts {@code GEOPOS geo:rider:{orderId} "{orderId}"} via Redis.
     * Falls back to the in-memory map on any exception.
     */
    public RiderLocation getLatestLocation(Integer orderId) {
        try {
            GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
            String geoKey  = GEO_KEY_PREFIX  + orderId;
            String tempKey = TEMP_KEY_PREFIX + orderId;
            String member  = String.valueOf(orderId);

            List<Point> positions = geoOps.position(geoKey, member);

            if (positions != null && !positions.isEmpty() && positions.get(0) != null) {
                Point point = positions.get(0);
                String tempStr = redisTemplate.opsForValue().get(tempKey);
                BigDecimal temp = (tempStr != null) ? new BigDecimal(tempStr) : BigDecimal.ZERO;

                // Redis Point: x=lng, y=lat
                RiderLocation redisLocation = new RiderLocation(
                        BigDecimal.valueOf(point.getY()),   // lat
                        BigDecimal.valueOf(point.getX()),   // lng
                        temp
                );

                log.debug("[GeoStore] Redis GEOPOS hit orderId={}", orderId);
                return redisLocation;
            }

        } catch (Exception ex) {
            log.warn("[GeoStore] Redis read failed for orderId={}. Using in-memory fallback. Cause: {}", orderId, ex.getMessage());
        }

        // Fallback: return last in-memory snapshot
        return fallbackStore.get(orderId);
    }

    // ── Value Object ──────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a rider's geospatial position at a point in time.
     */
    public static class RiderLocation {
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final BigDecimal temperature;
        private final OffsetDateTime timestamp;

        public RiderLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal temperature) {
            this.latitude    = latitude;
            this.longitude   = longitude;
            this.temperature = temperature;
            this.timestamp   = OffsetDateTime.now();
        }

        public BigDecimal getLatitude()    { return latitude; }
        public BigDecimal getLongitude()   { return longitude; }
        public BigDecimal getTemperature() { return temperature; }
        public OffsetDateTime getTimestamp() { return timestamp; }
    }
}
