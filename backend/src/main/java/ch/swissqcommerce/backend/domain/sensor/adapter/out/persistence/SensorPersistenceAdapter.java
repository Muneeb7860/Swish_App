package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SensorPersistenceAdapter implements SensorPort {

    private final SensorRepository repository;
    private final SensorReadingRepository readingRepository;

    @Override
    public Sensor save(Sensor sensor) {
        SensorEntity entity =
                SensorEntity.builder()
                        .sensorId(sensor.getSensorId())
                        .retailerId(sensor.getRetailerId())
                        .storeId(sensor.getStoreId())
                        .sensorType(sensor.getSensorType().name())
                        .status(sensor.getStatus())
                        .deviceKeyHash(sensor.getDeviceKeyHash())
                        .lastCalibratedAt(sensor.getLastCalibratedAt())
                        .calibrationStatus(sensor.getCalibrationStatus())
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
        return repository.findByRetailerIdOrderByCreatedAtDesc(retailerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Sensor> findByStoreId(String storeId) {
        return repository.findByStoreId(storeId).stream().map(this::toDomain).toList();
    }

    @Override
    public SensorReading saveReading(SensorReading reading) {
        SensorReadingEntity entity =
                SensorReadingEntity.builder()
                        .sensorId(reading.getSensorId())
                        .recordedAt(reading.getRecordedAt())
                        .metricType(reading.getMetricType())
                        .readingValue(reading.getValue())
                        .previousReadingHash(reading.getPreviousReadingHash())
                        .readingHash(reading.getReadingHash())
                        .build();
        return toDomain(readingRepository.save(entity));
    }

    @Override
    public List<SensorReading> recentReadings(String sensorId) {
        return readingRepository.findTop100BySensorIdOrderByRecordedAtDesc(sensorId).stream()
                .map(this::toDomain)
                .toList();
    }

    private SensorReading toDomain(SensorReadingEntity e) {
        return SensorReading.builder()
                .readingId(e.getReadingId())
                .sensorId(e.getSensorId())
                .recordedAt(e.getRecordedAt())
                .metricType(e.getMetricType())
                .value(e.getReadingValue())
                .previousReadingHash(e.getPreviousReadingHash())
                .readingHash(e.getReadingHash())
                .build();
    }

    private Sensor toDomain(SensorEntity e) {
        return Sensor.builder()
                .sensorId(e.getSensorId())
                .retailerId(e.getRetailerId())
                .storeId(e.getStoreId())
                .sensorType(SensorType.valueOf(e.getSensorType()))
                .status(e.getStatus())
                .deviceKeyHash(e.getDeviceKeyHash())
                .lastCalibratedAt(e.getLastCalibratedAt())
                .calibrationStatus(e.getCalibrationStatus())
                .build();
    }
}
