package ch.swissqcommerce.backend.domain.telemetry.port.out;

import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import java.util.List;
import java.util.Optional;

public interface TelemetryPort {
    OrderTelemetryLogEntity save(OrderTelemetryLogEntity log);
    List<OrderTelemetryLogEntity> findByOrderId(Integer orderId);
    Optional<Order> findOrderById(Integer orderId);
    Order saveOrder(Order order);
    Optional<Rider> findRiderById(String riderId);
    Rider saveRider(Rider rider);
    SecurityTrustLedger saveTrustLedger(SecurityTrustLedger ledger);
}
