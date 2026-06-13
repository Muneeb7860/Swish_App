package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorRepository extends JpaRepository<SensorEntity, String> {
    Optional<SensorEntity> findByDeviceKeyHash(String deviceKeyHash);

    List<SensorEntity> findByRetailerIdOrderByCreatedAtDesc(String retailerId);

    List<SensorEntity> findByStoreId(String storeId);
}
