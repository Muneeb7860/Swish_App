package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelemetryPersistenceAdapter implements TelemetryPort {

    @Autowired
    private OrderTelemetryLogRepository telemetryLogRepository;

    @Override
    public OrderTelemetryLog save(OrderTelemetryLog log) {
        return telemetryLogRepository.save(log);
    }
}
