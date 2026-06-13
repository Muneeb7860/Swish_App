package ch.swissqcommerce.backend.domain.dispatch.port.out;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.core.model.VehicleConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DispatchPort {
    VehicleConfig saveVehicleConfig(VehicleConfig config);

    Optional<VehicleConfig> findVehicleConfig(String vehicleType);

    GearScan saveGearScan(GearScan scan);

    List<GearScan> findGearScansByRider(String riderId);

    ActiveShipment saveActiveShipment(ActiveShipment shipment);

    Optional<ActiveShipment> findActiveShipmentByOrder(Integer orderId);

    List<ActiveShipment> findActiveShipmentsByRiderAndStatus(String riderId, String status);

    List<ActiveShipment> findActiveShipmentsByStatusIn(List<String> statuses);

    record EligibilityCriteria(
            String riderId,
            String vehicleType,
            String onboardingStatus,
            String riderFullName,
            String customerId,
            String customerFullName,
            BigDecimal weightKg,
            boolean gearExempt) {}

    boolean isRiderEligible(EligibilityCriteria criteria);
}
