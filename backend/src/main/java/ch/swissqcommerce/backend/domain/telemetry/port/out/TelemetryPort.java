package ch.swissqcommerce.backend.domain.telemetry.port.out;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;

public interface TelemetryPort {
    OrderTelemetryLog save(OrderTelemetryLog log);
}
