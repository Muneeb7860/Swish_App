package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<SensorEntity, String> {
    Optional<SensorEntity> findByDeviceKeyHash(String deviceKeyHash);
    List<SensorEntity> findByRetailerIdOrderByCreatedAtDesc(String retailerId);
    List<SensorEntity> findByStoreId(String storeId);
}
