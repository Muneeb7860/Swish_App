package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelemetryPersistenceAdapter implements TelemetryPort {

    @Autowired
    private OrderTelemetryLogRepository telemetryLogRepository;

    @Autowired
    private ch.swissqcommerce.backend.repository.OrderRepository orderRepository;

    @Autowired
    private ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository riderRepository;

    @Autowired
    private ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository trustLedgerRepository;

    @Override
    public OrderTelemetryLog save(OrderTelemetryLog log) {
        return telemetryLogRepository.save(log);
    }

    @Override
    public java.util.List<OrderTelemetryLog> findByOrderId(Integer orderId) {
        return telemetryLogRepository.findByOrderOrderIdOrderByDeviceTimestampDesc(orderId);
    }

    @Override
    public java.util.Optional<ch.swissqcommerce.backend.domain.transaction.core.model.Order> findOrderById(Integer orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    public ch.swissqcommerce.backend.domain.transaction.core.model.Order saveOrder(ch.swissqcommerce.backend.domain.transaction.core.model.Order order) {
        return orderRepository.save(order);
    }

    @Override
    public java.util.Optional<ch.swissqcommerce.backend.domain.enrollment.core.model.Rider> findRiderById(String riderId) {
        return riderRepository.findById(riderId);
    }

    @Override
    public ch.swissqcommerce.backend.domain.enrollment.core.model.Rider saveRider(ch.swissqcommerce.backend.domain.enrollment.core.model.Rider rider) {
        return riderRepository.save(rider);
    }

    @Override
    public ch.swissqcommerce.backend.model.SecurityTrustLedger saveTrustLedger(ch.swissqcommerce.backend.model.SecurityTrustLedger ledger) {
        return trustLedgerRepository.save(ledger);
    }
}
