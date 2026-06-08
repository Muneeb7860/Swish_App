package ch.swissqcommerce.backend.domain.telemetry.port.out;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface GeoLocationPort {
    void updateLocation(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp);
    RiderLocation getLatestLocation(Integer orderId);

    class RiderLocation {
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final BigDecimal temperature;
        private final OffsetDateTime timestamp;

        public RiderLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal temperature) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.temperature = temperature;
            this.timestamp = OffsetDateTime.now();
        }

        public RiderLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal temperature, OffsetDateTime timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }

        public BigDecimal getLatitude() { return latitude; }
        public BigDecimal getLongitude() { return longitude; }
        public BigDecimal getTemperature() { return temperature; }
        public OffsetDateTime getTimestamp() { return timestamp; }
    }
}
