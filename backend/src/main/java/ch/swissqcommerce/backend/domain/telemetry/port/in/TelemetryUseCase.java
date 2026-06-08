package ch.swissqcommerce.backend.domain.telemetry.port.in;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.telemetry.port.out.GeoLocationPort;
import java.math.BigDecimal;

public interface TelemetryUseCase {
    OrderTelemetryLog recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, 
                                     BigDecimal temp, boolean dryIceInjected);
    boolean isThermalBreachActive(Integer orderId, BigDecimal currentTemp);
    void injectDryIce(Integer orderId);

    boolean updateLocation(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp);
    GeoLocationPort.RiderLocation getLatestLocation(Integer orderId);
    void queueTick(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp, boolean dryIceInjected);
    void flushTickBuffer();
    void cleanupOrder(Integer orderId);
}
