package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Long> {
    List<SensorReadingEntity> findTop100BySensorIdOrderByRecordedAtDesc(String sensorId);
}
