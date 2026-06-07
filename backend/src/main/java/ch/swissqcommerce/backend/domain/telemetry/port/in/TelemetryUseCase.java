package ch.swissqcommerce.backend.domain.telemetry.port.in;

import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import java.math.BigDecimal;

public interface TelemetryUseCase {
    OrderTelemetryLogEntity recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, 
                                     BigDecimal temp, boolean dryIceInjected);
    boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp);
    void injectDryIce(Integer orderId);
}
