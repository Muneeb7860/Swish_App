package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderTelemetryLogEntityRepository extends JpaRepository<OrderTelemetryLogEntity, Integer> {
    List<OrderTelemetryLogEntity> findByOrderOrderIdOrderByDeviceTimestampDesc(Integer orderId);
}
