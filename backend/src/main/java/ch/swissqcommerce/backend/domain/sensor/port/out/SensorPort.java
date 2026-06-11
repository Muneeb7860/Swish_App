package ch.swissqcommerce.backend.domain.sensor.port.out;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;

import java.util.List;
import java.util.Optional;

public interface SensorPort {
    Sensor save(Sensor sensor);
    Optional<Sensor> findById(String sensorId);
    Optional<Sensor> findByDeviceKeyHash(String deviceKeyHash);
    List<Sensor> findByRetailerId(String retailerId);
}
