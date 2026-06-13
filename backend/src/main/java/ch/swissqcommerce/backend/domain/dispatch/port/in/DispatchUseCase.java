package ch.swissqcommerce.backend.domain.dispatch.port.in;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import java.math.BigDecimal;
import java.util.List;

public interface DispatchUseCase {
    GearScan submitGearScan(
            String riderId, String gearType, String verificationStatus, String imageUrl);

    ActiveShipment updateRiderGps(String riderId, BigDecimal lat, BigDecimal lng);

    List<Integer> runReallocationAudit();

    ActiveShipment assignOrder(Integer orderId, String riderId, BigDecimal weightKg);

    ActiveShipment updateShipmentStatus(Integer orderId, String status);
}
