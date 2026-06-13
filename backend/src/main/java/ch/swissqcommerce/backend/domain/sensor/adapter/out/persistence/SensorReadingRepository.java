package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Long> {
    List<SensorReadingEntity> findTop100BySensorIdOrderByRecordedAtDesc(String sensorId);
}
