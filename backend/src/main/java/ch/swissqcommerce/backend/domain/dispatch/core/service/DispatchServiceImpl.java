package ch.swissqcommerce.backend.domain.dispatch.core.service;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.core.model.ShipmentStatus;
import ch.swissqcommerce.backend.domain.dispatch.port.in.DispatchUseCase;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchServiceImpl implements DispatchUseCase {

    private final DispatchPort dispatchPort;
    private final EnrollmentOutPort enrollmentOutPort;
    private final OrderPort orderPort;

    @Override
    @Transactional
    public GearScan submitGearScan(String riderId, String gearType, String verificationStatus, String imageUrl) {
        // Confirm rider exists
        if (enrollmentOutPort.findRiderById(riderId).isEmpty()) {
            throw new NoSuchElementException("Rider not found: " + riderId);
        }

        GearScan scan = GearScan.builder()
                .scanId(UUID.randomUUID().toString())
                .riderId(riderId)
                .scanTime(OffsetDateTime.now())
                .gearType(gearType)
                .verificationStatus(verificationStatus)
                .imageUrl(imageUrl)
                .checkedBy("SYSTEM_AUTO")
                .build();

        return dispatchPort.saveGearScan(scan);
    }

    @Override
    @Transactional
    public ActiveShipment updateRiderGps(String riderId, BigDecimal lat, BigDecimal lng) {
        Rider rider = enrollmentOutPort.findRiderById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        rider.setActiveLat(lat);
        rider.setActiveLng(lng);
        enrollmentOutPort.saveRider(rider);

        List<ActiveShipment> activeShipments = dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "ASSIGNED");
        activeShipments.addAll(dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "PICKING_UP"));
        activeShipments.addAll(dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "DELIVERING"));

        ActiveShipment updatedShipment = null;
        OffsetDateTime now = OffsetDateTime.now();

        for (ActiveShipment shipment : activeShipments) {
            BigDecimal lastLat = shipment.getLastLat();
            BigDecimal lastLng = shipment.getLastLng();

            if (lastLat != null && lastLng != null) {
                double distance = calculateHaversineDistance(lastLat, lastLng, lat, lng);
                if (distance < 10.0) {
                    if (shipment.getStationarySince() == null) {
                        shipment.setStationarySince(now);
                    }
                } else {
                    shipment.setStationarySince(null);
                }
            } else {
                shipment.setStationarySince(null);
            }

            shipment.setLastLat(lat);
            shipment.setLastLng(lng);
            shipment.setLastGpsUpdate(now);
            shipment.setUpdatedAt(now);
            updatedShipment = dispatchPort.saveActiveShipment(shipment);
        }

        return updatedShipment;
    }

    @Override
    @Transactional
    public List<Integer> runReallocationAudit() {
        List<ActiveShipment> activeShipments = dispatchPort.findActiveShipmentsByStatusIn(
                List.of("ASSIGNED", "PICKING_UP", "DELIVERING")
        );

        List<Integer> reallocatedOrders = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        List<ActiveShipment> shipmentsToReallocate = new ArrayList<>();
        List<Integer> orderIdsToFetch = new ArrayList<>();

        for (ActiveShipment shipment : activeShipments) {
            if (shipment.getStationarySince() != null && shipment.getStationarySince().isBefore(now.minusMinutes(10))) {
                shipment.setStatus(ShipmentStatus.REALLOCATED);
                shipment.setUpdatedAt(now);
                shipmentsToReallocate.add(shipment);
                orderIdsToFetch.add(shipment.getOrderId());
            }
        }

        if (!shipmentsToReallocate.isEmpty()) {
            for (ActiveShipment shipment : shipmentsToReallocate) {
                dispatchPort.saveActiveShipment(shipment);
            }

            List<Order> orders = new ArrayList<>();
            for (Integer id : orderIdsToFetch) {
                orderPort.findById(id).ifPresent(orders::add);
            }
            for (Order order : orders) {
                order.setStatus("pending");
                order.setRider(null);
                reallocatedOrders.add(order.getOrderId());
                orderPort.save(order);
            }
        }

        return reallocatedOrders;
    }

    @Override
    @Transactional
    public ActiveShipment assignOrder(Integer orderId, String riderId, BigDecimal weightKg) {
        Rider rider = enrollmentOutPort.findRiderById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        Order order = orderPort.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        String customerId = order.getCustomer() != null ? order.getCustomer().getCustomerId() : null;
        String customerName = order.getCustomer() != null ? order.getCustomer().getFullName() : null;

        DispatchPort.EligibilityCriteria criteria = new DispatchPort.EligibilityCriteria(
                riderId,
                rider.getVehicleType(),
                rider.getOnboardingStatus(),
                rider.getFullName(),
                customerId,
                customerName,
                weightKg
        );

        boolean eligible = dispatchPort.isRiderEligible(criteria);
        if (!eligible) {
            throw new IllegalStateException("Rider is not eligible for order assignment due to gear verification, vehicle weight limits, or self-matching restrictions.");
        }

        // Assign order to rider
        order.setRider(rider);
        orderPort.save(order);

        // Update active shipment entry
        ActiveShipment shipment = dispatchPort.findActiveShipmentByOrder(orderId)
                .orElseGet(() -> ActiveShipment.builder()
                        .shipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .orderId(orderId)
                        .totalWeightKg(weightKg)
                        .build());

        shipment.setRiderId(riderId);
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setAssignedAt(OffsetDateTime.now());
        shipment.setUpdatedAt(OffsetDateTime.now());

        return dispatchPort.saveActiveShipment(shipment);
    }

    @Override
    @Transactional
    public ActiveShipment updateShipmentStatus(Integer orderId, String status) {
        ActiveShipment shipment = dispatchPort.findActiveShipmentByOrder(orderId)
                .orElseThrow(() -> new NoSuchElementException("Active shipment not found for order: " + orderId));

        shipment.setStatus(ShipmentStatus.valueOf(status.toUpperCase()));
        shipment.setUpdatedAt(OffsetDateTime.now());
        return dispatchPort.saveActiveShipment(shipment);
    }

    private double calculateHaversineDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double R = 6371e3; // metres
        double phi1 = lat1.doubleValue() * Math.PI / 180;
        double phi2 = lat2.doubleValue() * Math.PI / 180;
        double deltaPhi = (lat2.doubleValue() - lat1.doubleValue()) * Math.PI / 180;
        double deltaLambda = (lon2.doubleValue() - lon1.doubleValue()) * Math.PI / 180;

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                   Math.cos(phi1) * Math.cos(phi2) *
                   Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
