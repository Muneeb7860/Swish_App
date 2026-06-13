package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderTelemetryLogRepository
        extends JpaRepository<OrderTelemetryLogEntity, Integer> {
    List<OrderTelemetryLogEntity> findByOrderIdOrderByDeviceTimestampDesc(Integer orderId);
}
