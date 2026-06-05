package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderTelemetryLogRepository extends JpaRepository<OrderTelemetryLog, Integer> {
    List<OrderTelemetryLog> findByOrderOrderIdOrderByDeviceTimestampDesc(Integer orderId);
}
