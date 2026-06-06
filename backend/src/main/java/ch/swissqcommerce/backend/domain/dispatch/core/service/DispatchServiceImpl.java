package ch.swissqcommerce.backend.domain.dispatch.core.service;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.port.in.DispatchUseCase;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.repository.OrderRepository;
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
    private final RiderRepository riderRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public GearScan submitGearScan(String riderId, String gearType, String verificationStatus, String imageUrl) {
        // Confirm rider exists
        if (!riderRepository.existsById(riderId)) {
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
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        rider.setActiveLat(lat);
        rider.setActiveLng(lng);
        riderRepository.save(rider);

        List<ActiveShipment> activeShipments = dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "ASSIGNED");
        activeShipments.addAll(dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "PICKING_UP"));
        activeShipments.addAll(dispatchPort.findActiveShipmentsByRiderAndStatus(riderId, "DELIVERING"));

        ActiveShipment updatedShipment = null;
        OffsetDateTime now = OffsetDateTime.now();

        for (ActiveShipment shipment : activeShipments) {
            BigDecimal lastLat = shipment.getLastLat();
            BigDecimal lastLng = shipment.getLastLng();

            if (lastLat != null && lastLng != null) {
                // Coords match -> Rider is stationary
                if (lastLat.compareTo(lat) == 0 && lastLng.compareTo(lng) == 0) {
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

        for (ActiveShipment shipment : activeShipments) {
            if (shipment.getStationarySince() != null) {
                // If stationary since is > 10 minutes ago
                if (shipment.getStationarySince().isBefore(now.minusMinutes(10))) {
                    shipment.setStatus("REALLOCATED");
                    shipment.setUpdatedAt(now);
                    dispatchPort.saveActiveShipment(shipment);

                    Order order = orderRepository.findById(shipment.getOrderId()).orElse(null);
                    if (order != null) {
                        order.setStatus("pending");
                        order.setRider(null);
                        orderRepository.save(order);
                        reallocatedOrders.add(order.getOrderId());
                    }
                }
            }
        }

        return reallocatedOrders;
    }

    @Override
    @Transactional
    public ActiveShipment assignOrder(Integer orderId, String riderId, BigDecimal weightKg) {
        // Enforce anti-fraud, gear verification, and weight capacity checks via PostgreSQL Stored Procedure
        boolean eligible = dispatchPort.isRiderEligible(riderId, orderId, weightKg);
        if (!eligible) {
            throw new IllegalStateException("Rider is not eligible for order assignment due to gear verification, vehicle weight limits, or self-matching restrictions.");
        }

        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        // Assign order to rider
        order.setRider(rider);
        orderRepository.save(order);

        // Update active shipment entry
        ActiveShipment shipment = dispatchPort.findActiveShipmentByOrder(orderId)
                .orElseGet(() -> ActiveShipment.builder()
                        .shipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .orderId(orderId)
                        .totalWeightKg(weightKg)
                        .build());

        shipment.setRiderId(riderId);
        shipment.setStatus("ASSIGNED");
        shipment.setAssignedAt(OffsetDateTime.now());
        shipment.setUpdatedAt(OffsetDateTime.now());

        return dispatchPort.saveActiveShipment(shipment);
    }

    @Override
    @Transactional
    public ActiveShipment updateShipmentStatus(Integer orderId, String status) {
        ActiveShipment shipment = dispatchPort.findActiveShipmentByOrder(orderId)
                .orElseThrow(() -> new NoSuchElementException("Active shipment not found for order: " + orderId));

        shipment.setStatus(status);
        shipment.setUpdatedAt(OffsetDateTime.now());
        return dispatchPort.saveActiveShipment(shipment);
    }
}
