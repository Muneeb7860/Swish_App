package ch.swissqcommerce.backend.domain.sensor.port.out;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;

import java.util.List;
import java.util.Optional;

public interface SensorPort {
    Sensor save(Sensor sensor);
    Optional<Sensor> findById(String sensorId);
    Optional<Sensor> findByDeviceKeyHash(String deviceKeyHash);
    List<Sensor> findByRetailerId(String retailerId);

    /** Append a telemetry reading to the time-series store (TimescaleDB hypertable). */
    SensorReading saveReading(SensorReading reading);
    List<SensorReading> recentReadings(String sensorId);
    List<Sensor> findByStoreId(String storeId);
}
