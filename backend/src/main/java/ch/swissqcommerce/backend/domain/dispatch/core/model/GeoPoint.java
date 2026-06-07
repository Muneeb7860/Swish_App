package ch.swissqcommerce.backend.domain.dispatch.core.model;

import lombok.Value;
import java.math.BigDecimal;

@Value
public class GeoPoint {
    BigDecimal latitude;
    BigDecimal longitude;

    public GeoPoint(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
