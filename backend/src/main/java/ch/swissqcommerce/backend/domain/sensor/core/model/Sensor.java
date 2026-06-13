package ch.swissqcommerce.backend.domain.sensor.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A provisioned IoT device belonging to a retailer hub. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sensor {
    private String sensorId;
    private String retailerId;
    private String storeId;
    private SensorType sensorType;

    /** PROVISIONED, ACTIVE, or DECOMMISSIONED. */
    private String status;

    /** SHA-256 hash of the device key; plaintext returned only once at provisioning. */
    private String deviceKeyHash;

    private java.time.OffsetDateTime lastCalibratedAt;
    private String calibrationStatus;
}
