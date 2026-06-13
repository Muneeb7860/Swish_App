package ch.swissqcommerce.backend.domain.dispatch.core.model;

import java.math.BigDecimal;
import lombok.Value;

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
