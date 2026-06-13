package ch.swissqcommerce.backend.domain.telemetry.adapter.out.geo;

import ch.swissqcommerce.backend.domain.telemetry.port.out.GeoLocationPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGeoStoreAdapter implements GeoLocationPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryGeoStoreAdapter.class);

    private static final String GEO_KEY_PREFIX = "geo:rider:";
    private static final String TEMP_KEY_PREFIX = "temp:rider:";

    private final ConcurrentHashMap<Integer, GeoLocationPort.RiderLocation> fallbackStore =
            new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public InMemoryGeoStoreAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void updateLocation(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        fallbackStore.put(orderId, new GeoLocationPort.RiderLocation(lat, lng, temp));

        try {
            GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
            String geoKey = GEO_KEY_PREFIX + orderId;
            String tempKey = TEMP_KEY_PREFIX + orderId;
            String member = String.valueOf(orderId);

            geoOps.add(geoKey, new Point(lng.doubleValue(), lat.doubleValue()), member);

            redisTemplate.opsForValue().set(tempKey, temp.toPlainString());
            redisTemplate.expire(geoKey, java.time.Duration.ofHours(1));
            redisTemplate.expire(tempKey, java.time.Duration.ofHours(1));

            log.debug(
                    "[GeoStoreAdapter] Redis GEOADD orderId={} lat={} lng={} temp={}",
                    orderId,
                    lat,
                    lng,
                    temp);

        } catch (Exception ex) {
            log.warn(
                    "[GeoStoreAdapter] Redis write failed for orderId={}. Using in-memory fallback."
                            + " Cause: {}",
                    orderId,
                    ex.getMessage());
        }
    }

    @Override
    public GeoLocationPort.RiderLocation getLatestLocation(Integer orderId) {
        try {
            GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
            String geoKey = GEO_KEY_PREFIX + orderId;
            String tempKey = TEMP_KEY_PREFIX + orderId;
            String member = String.valueOf(orderId);

            List<Point> positions = geoOps.position(geoKey, member);

            if (positions != null && !positions.isEmpty() && positions.get(0) != null) {
                Point point = positions.get(0);
                String tempStr = redisTemplate.opsForValue().get(tempKey);
                BigDecimal temp = (tempStr != null) ? new BigDecimal(tempStr) : BigDecimal.ZERO;

                GeoLocationPort.RiderLocation redisLocation =
                        new GeoLocationPort.RiderLocation(
                                BigDecimal.valueOf(point.getY()), // lat
                                BigDecimal.valueOf(point.getX()), // lng
                                temp);

                log.debug("[GeoStoreAdapter] Redis GEOPOS hit orderId={}", orderId);
                return redisLocation;
            }

        } catch (Exception ex) {
            log.warn(
                    "[GeoStoreAdapter] Redis read failed for orderId={}. Using in-memory fallback."
                            + " Cause: {}",
                    orderId,
                    ex.getMessage());
        }

        return fallbackStore.get(orderId);
    }
}
