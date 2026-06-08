package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TelemetryPersistenceAdapter implements TelemetryPort {

    private final OrderTelemetryLogRepository telemetryLogRepository;

    @Override
    public OrderTelemetryLogEntity save(OrderTelemetryLogEntity log) {
        return log;
    }

    @Override
    public List<OrderTelemetryLogEntity> findByOrderId(Integer orderId) {
        return telemetryLogRepository.findByOrderIdOrderByDeviceTimestampDesc(orderId);
    }

    @Override
    public Optional<Order> findOrderById(Integer orderId) {
        return Optional.empty();
    }

    @Override
    public Order saveOrder(Order order) {
        return order;
    }

    @Override
    public Optional<Rider> findRiderById(String riderId) {
        return Optional.empty();
    }

    @Override
    public Rider saveRider(Rider rider) {
        return rider;
    }

    @Override
    public SecurityTrustLedger saveTrustLedger(SecurityTrustLedger ledger) {
        return ledger;
    }
}
