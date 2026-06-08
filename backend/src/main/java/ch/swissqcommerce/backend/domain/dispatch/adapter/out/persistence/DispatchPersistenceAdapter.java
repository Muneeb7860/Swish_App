package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;


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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ch.swissqcommerce.backend.domain.dispatch.core.model.RouteCoordinates;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GeoPoint;
import ch.swissqcommerce.backend.domain.dispatch.core.model.ShipmentStatus;

@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchPersistenceAdapter implements DispatchPort {

    private final VehicleConfigRepository vehicleConfigRepository;
    private final GearScanRepository gearScanRepository;
    private final ActiveShipmentRepository activeShipmentRepository;

    private VehicleConfigEntity mapToEntity(VehicleConfig config) {
        if (config == null) return null;
        return VehicleConfigEntity.builder()
                .vehicleType(config.getVehicleType())
                .maxWeightKg(config.getMaxWeightKg())
                .averageSpeedKmh(config.getAverageSpeedKmh())
                .build();
    }

    private VehicleConfig mapToDomain(VehicleConfigEntity entity) {
        if (entity == null) return null;
        return VehicleConfig.builder()
                .vehicleType(entity.getVehicleType())
                .maxWeightKg(entity.getMaxWeightKg())
                .averageSpeedKmh(entity.getAverageSpeedKmh())
                .build();
    }

    private GearScanEntity mapToEntity(GearScan scan) {
        if (scan == null) return null;
        return GearScanEntity.builder()
                .scanId(scan.getScanId())
                .riderId(scan.getRiderId())
                .scanTime(scan.getScanTime())
                .gearType(scan.getGearType())
                .verificationStatus(scan.getVerificationStatus())
                .imageUrl(scan.getImageUrl())
                .checkedBy(scan.getCheckedBy())
                .build();
    }

    private GearScan mapToDomain(GearScanEntity entity) {
        if (entity == null) return null;
        return GearScan.builder()
                .scanId(entity.getScanId())
                .riderId(entity.getRiderId())
                .scanTime(entity.getScanTime())
                .gearType(entity.getGearType())
                .verificationStatus(entity.getVerificationStatus())
                .imageUrl(entity.getImageUrl())
                .checkedBy(entity.getCheckedBy())
                .build();
    }

    private ActiveShipmentEntity mapToEntity(ActiveShipment shipment) {
        if (shipment == null) return null;
        return ActiveShipmentEntity.builder()
                .shipmentId(shipment.getShipmentId())
                .orderId(shipment.getOrderId())
                .riderId(shipment.getRiderId())
                .status(shipment.getStatus() != null ? shipment.getStatus().name() : null)
                .totalWeightKg(shipment.getTotalWeightKg())
                .assignedAt(shipment.getAssignedAt())
                .lastGpsUpdate(shipment.getLastGpsUpdate())
                .lastLat(shipment.getLastLat())
                .lastLng(shipment.getLastLng())
                .stationarySince(shipment.getStationarySince())
                .updatedAt(shipment.getUpdatedAt())
                .build();
    }

    private ActiveShipment mapToDomain(ActiveShipmentEntity entity) {
        if (entity == null) return null;
        return ActiveShipment.builder()
                .shipmentId(entity.getShipmentId())
                .orderId(entity.getOrderId())
                .riderId(entity.getRiderId())
                .status(entity.getStatus() != null ? ShipmentStatus.valueOf(entity.getStatus().toUpperCase()) : null)
                .totalWeightKg(entity.getTotalWeightKg())
                .assignedAt(entity.getAssignedAt())
                .lastGpsUpdate(entity.getLastGpsUpdate())
                .lastLat(entity.getLastLat())
                .lastLng(entity.getLastLng())
                .stationarySince(entity.getStationarySince())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @Override
    public VehicleConfig saveVehicleConfig(VehicleConfig config) {
        VehicleConfigEntity entity = mapToEntity(config);
        VehicleConfigEntity saved = vehicleConfigRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<VehicleConfig> findVehicleConfig(String vehicleType) {
        return vehicleConfigRepository.findById(vehicleType).map(this::mapToDomain);
    }

    @Override
    public GearScan saveGearScan(GearScan scan) {
        GearScanEntity entity = mapToEntity(scan);
        GearScanEntity saved = gearScanRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public List<GearScan> findGearScansByRider(String riderId) {
        return gearScanRepository.findByRiderIdOrderByScanTimeDesc(riderId).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public ActiveShipment saveActiveShipment(ActiveShipment shipment) {
        ActiveShipmentEntity entity = mapToEntity(shipment);
        ActiveShipmentEntity saved = activeShipmentRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<ActiveShipment> findActiveShipmentByOrder(Integer orderId) {
        return activeShipmentRepository.findByOrderId(orderId).map(this::mapToDomain);
    }

    @Override
    public List<ActiveShipment> findActiveShipmentsByRiderAndStatus(String riderId, String status) {
        return activeShipmentRepository.findByRiderIdAndStatus(riderId, status).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public List<ActiveShipment> findActiveShipmentsByStatusIn(List<String> statuses) {
        return activeShipmentRepository.findByStatusIn(statuses).stream()
                .map(this::mapToDomain)
                .toList();
    }

    @Override
    public boolean isRiderEligible(EligibilityCriteria criteria) {
        if (!"active".equalsIgnoreCase(criteria.onboardingStatus())) {
            log.warn("Rider {} is not active.", criteria.riderId());
            return false;
        }

        VehicleConfigEntity vehicleConfig = vehicleConfigRepository.findById(criteria.vehicleType()).orElse(null);
        if (vehicleConfig == null) {
            log.warn("Vehicle configuration not found for type {}.", criteria.vehicleType());
            return false;
        }

        BigDecimal limitWithTolerance = vehicleConfig.getMaxWeightKg().multiply(new BigDecimal("1.10"));
        if (criteria.weightKg().compareTo(limitWithTolerance) > 0) {
            log.warn("Order weight {} exceeds vehicle max weight limit {} (including 10% tolerance).", criteria.weightKg(), limitWithTolerance);
            return false;
        }

        if (!criteria.gearExempt()) {
            OffsetDateTime oneDayAgo = OffsetDateTime.now().minusDays(1);
            List<GearScanEntity> scans = gearScanRepository.findByRiderIdOrderByScanTimeDesc(criteria.riderId());
            boolean hasValidGear = scans.stream()
                    .filter(s -> "THERMAL_BAG".equals(s.getGearType()))
                    .filter(s -> "PASSED".equals(s.getVerificationStatus()))
                    .anyMatch(s -> {
                        OffsetDateTime time = s.getScanTime() != null ? s.getScanTime() : OffsetDateTime.now();
                        return time.isAfter(oneDayAgo);
                    });

            if (!hasValidGear) {
                log.warn("Rider {} has no valid passed thermal bag gear scan in the last 24 hours.", criteria.riderId());
                return false;
            }
        }

        if (criteria.customerId() == null) {
            log.warn("Customer profile not found in criteria.");
            return false;
        }

        if (criteria.customerId().equals(criteria.riderId())) {
            log.warn("Self-matching detected. Customer ID matches Rider profile: customer={}, rider={}", 
                    criteria.customerId(), criteria.riderId());
            return false;
        }

        return true;
    }
}
