package ch.swissqcommerce.backend.domain.telemetry.port.out;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import java.util.List;

public interface TelemetryPort {
    OrderTelemetryLog save(OrderTelemetryLog log);
    List<OrderTelemetryLog> findByOrderId(Integer orderId);
}
