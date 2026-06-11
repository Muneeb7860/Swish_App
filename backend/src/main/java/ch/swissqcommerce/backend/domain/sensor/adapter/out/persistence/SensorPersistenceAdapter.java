package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SensorPersistenceAdapter implements SensorPort {

    private final SensorRepository repository;

    @Override
    public Sensor save(Sensor sensor) {
        SensorEntity entity = SensorEntity.builder()
                .sensorId(sensor.getSensorId())
                .retailerId(sensor.getRetailerId())
                .storeId(sensor.getStoreId())
                .sensorType(sensor.getSensorType().name())
                .status(sensor.getStatus())
                .deviceKeyHash(sensor.getDeviceKeyHash())
                .build();
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Sensor> findById(String sensorId) {
        return repository.findById(sensorId).map(this::toDomain);
    }

    @Override
    public Optional<Sensor> findByDeviceKeyHash(String deviceKeyHash) {
        return repository.findByDeviceKeyHash(deviceKeyHash).map(this::toDomain);
    }

    @Override
    public List<Sensor> findByRetailerId(String retailerId) {
        return repository.findByRetailerIdOrderByCreatedAtDesc(retailerId)
                .stream().map(this::toDomain).toList();
    }

    private Sensor toDomain(SensorEntity e) {
        return Sensor.builder()
                .sensorId(e.getSensorId())
                .retailerId(e.getRetailerId())
                .storeId(e.getStoreId())
                .sensorType(SensorType.valueOf(e.getSensorType()))
                .status(e.getStatus())
                .deviceKeyHash(e.getDeviceKeyHash())
                .build();
    }
}
