package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.core.model.VehicleConfig;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchPersistenceAdapter implements DispatchPort {

    private final VehicleConfigRepository vehicleConfigRepository;
    private final GearScanRepository gearScanRepository;
    private final ActiveShipmentRepository activeShipmentRepository;
    private final RiderRepository riderRepository;
    private final OrderRepository orderRepository;

    @Override
    public VehicleConfig saveVehicleConfig(VehicleConfig config) {
        return vehicleConfigRepository.save(config);
    }

    @Override
    public Optional<VehicleConfig> findVehicleConfig(String vehicleType) {
        return vehicleConfigRepository.findById(vehicleType);
    }

    @Override
    public GearScan saveGearScan(GearScan scan) {
        return gearScanRepository.save(scan);
    }

    @Override
    public List<GearScan> findGearScansByRider(String riderId) {
        return gearScanRepository.findByRiderIdOrderByScanTimeDesc(riderId);
    }

    @Override
    public ActiveShipment saveActiveShipment(ActiveShipment shipment) {
        return activeShipmentRepository.save(shipment);
    }

    @Override
    public Optional<ActiveShipment> findActiveShipmentByOrder(Integer orderId) {
        return activeShipmentRepository.findByOrderId(orderId);
    }

    @Override
    public List<ActiveShipment> findActiveShipmentsByRiderAndStatus(String riderId, String status) {
        return activeShipmentRepository.findByRiderIdAndStatus(riderId, status);
    }

    @Override
    public List<ActiveShipment> findActiveShipmentsByStatusIn(List<String> statuses) {
        return activeShipmentRepository.findByStatusIn(statuses);
    }

    @Override
    public boolean isRiderEligible(String riderId, Integer orderId, BigDecimal weightKg) {
        // 1. Fetch Rider profile
        Rider rider = riderRepository.findById(riderId).orElse(null);
        if (rider == null || !"active".equalsIgnoreCase(rider.getOnboardingStatus())) {
            log.warn("Rider {} is not active or not found.", riderId);
            return false;
        }

        // 2. Fetch Vehicle Config
        VehicleConfig vehicleConfig = vehicleConfigRepository.findById(rider.getVehicleType()).orElse(null);
        if (vehicleConfig == null) {
            log.warn("Vehicle configuration not found for type {}.", rider.getVehicleType());
            return false;
        }

        if (weightKg.compareTo(vehicleConfig.getMaxWeightKg()) > 0) {
            log.warn("Order weight {} exceeds vehicle max weight limit {}.", weightKg, vehicleConfig.getMaxWeightKg());
            return false;
        }

        // 3. Verify gear scan check (must have passed scan for THERMAL_BAG within last 24h)
        OffsetDateTime oneDayAgo = OffsetDateTime.now().minusDays(1);
        List<GearScan> scans = gearScanRepository.findByRiderIdOrderByScanTimeDesc(riderId);
        boolean hasValidGear = scans.stream()
                .filter(s -> "THERMAL_BAG".equals(s.getGearType()))
                .filter(s -> "PASSED".equals(s.getVerificationStatus()))
                .anyMatch(s -> {
                    // Check if scanTime is present and within 24h (handling null for newly unsaved entities)
                    OffsetDateTime time = s.getScanTime() != null ? s.getScanTime() : OffsetDateTime.now();
                    return time.isAfter(oneDayAgo);
                });

        if (!hasValidGear) {
            log.warn("Rider {} has no valid passed thermal bag gear scan in the last 24 hours.", riderId);
            return false;
        }

        // 4. Anti-Self-Matching / Fraud check
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getCustomer() == null) {
            log.warn("Order {} or customer profile not found.", orderId);
            return false;
        }

        Customer customer = order.getCustomer();
        if (customer.getCustomerId().equals(riderId) || 
            customer.getFullName().equalsIgnoreCase(rider.getFullName())) {
            log.warn("Self-matching detected. Customer ID or name matches Rider profile: customer={}, rider={}", 
                    customer.getCustomerId(), riderId);
            return false;
        }

        return true;
    }
}
