package ch.swissqcommerce.backend.domain.sensor.port.in;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Sensor (device) provisioning use cases (BRD FR-01 sensor provisioning). */
public interface SensorUseCase {

    /** Provisioning result; {@code deviceKey} plaintext is returned exactly once. */
    record ProvisionResult(Sensor sensor, String deviceKey) {}

    ProvisionResult provision(String retailerId, String storeId, SensorType type);

    Sensor activate(String sensorId);

    Sensor decommission(String sensorId);

    Optional<Sensor> getSensor(String sensorId);

    List<Sensor> listByRetailer(String retailerId);

    /** Resolve an ACTIVE sensor by raw device key (hashed internally). */
    Optional<Sensor> authenticateByDeviceKey(String deviceKey);

    /** Device-authenticated telemetry ingestion: the device key must match an
     *  ACTIVE sensor. Writes a reading to the time-series store. */
    SensorReading recordReading(String deviceKey, String metricType, BigDecimal value);

    List<SensorReading> getRecentReadings(String sensorId);
}
