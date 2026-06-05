package ch.swissqcommerce.backend.domain.telemetry.port.in;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import java.math.BigDecimal;

public interface TelemetryUseCase {
    OrderTelemetryLog recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, 
                                     BigDecimal temp, boolean dryIceInjected);
    boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp);
    void injectDryIce(Integer orderId);
}
