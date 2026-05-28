package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.OrderTelemetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderTelemetryLogRepository extends JpaRepository<OrderTelemetryLog, Integer> {
    List<OrderTelemetryLog> findByOrderOrderIdOrderByDeviceTimestampDesc(Integer orderId);
}
