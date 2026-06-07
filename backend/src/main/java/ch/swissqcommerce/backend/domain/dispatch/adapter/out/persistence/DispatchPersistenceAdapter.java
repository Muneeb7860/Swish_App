package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.ActiveShipmentEntity;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.GearScanEntity;
import ch.swissqcommerce.backend.domain.dispatch.core.model.VehicleConfig;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.VehicleConfigEntity;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort.EligibilityCriteria;
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
    public boolean isRiderEligible(EligibilityCriteria criteria) {
        if (!"active".equalsIgnoreCase(criteria.onboardingStatus())) {
            log.warn("Rider {} is not active.", criteria.riderId());
            return false;
        }

        // 2. Fetch Vehicle Config
        VehicleConfig vehicleConfig = vehicleConfigRepository.findById(criteria.vehicleType()).orElse(null);
        if (vehicleConfig == null) {
            log.warn("Vehicle configuration not found for type {}.", criteria.vehicleType());
            return false;
        }

        if (criteria.weightKg().compareTo(vehicleConfig.getMaxWeightKg()) > 0) {
            log.warn("Order weight {} exceeds vehicle max weight limit {}.", criteria.weightKg(), vehicleConfig.getMaxWeightKg());
            return false;
        }

        // 3. Verify gear scan check (must have passed scan for THERMAL_BAG within last 24h)
        OffsetDateTime oneDayAgo = OffsetDateTime.now().minusDays(1);
        List<GearScan> scans = gearScanRepository.findByRiderIdOrderByScanTimeDesc(criteria.riderId());
        boolean hasValidGear = scans.stream()
                .filter(s -> "THERMAL_BAG".equals(s.getGearType()))
                .filter(s -> "PASSED".equals(s.getVerificationStatus()))
                .anyMatch(s -> {
                    // Check if scanTime is present and within 24h (handling null for newly unsaved entities)
                    OffsetDateTime time = s.getScanTime() != null ? s.getScanTime() : OffsetDateTime.now();
                    return time.isAfter(oneDayAgo);
                });

        if (!hasValidGear) {
            log.warn("Rider {} has no valid passed thermal bag gear scan in the last 24 hours.", criteria.riderId());
            return false;
        }

        // 4. Anti-Self-Matching / Fraud check
        if (criteria.customerId() == null) {
            log.warn("Customer profile not found in criteria.");
            return false;
        }

        if (criteria.customerId().equals(criteria.riderId()) || 
            (criteria.customerFullName() != null && criteria.customerFullName().equalsIgnoreCase(criteria.riderFullName()))) {
            log.warn("Self-matching detected. Customer ID or name matches Rider profile: customer={}, rider={}", 
                    criteria.customerId(), criteria.riderId());
            return false;
        }

        return true;
    }
}
