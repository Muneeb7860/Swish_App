package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.port.in.DispatchUseCase;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.GearScanRepository;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.ActiveShipmentRepository;
import ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.VehicleConfigRepository;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import ch.swissqcommerce.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DispatchIntegrationTest {

    @Autowired
    private DispatchUseCase dispatchUseCase;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderPort orderPort;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private GearScanRepository gearScanRepository;

    @Autowired
    private ActiveShipmentRepository activeShipmentRepository;

    @Autowired
    private VehicleConfigRepository vehicleConfigRepository;

    private Customer customer;
    private Order order;
    private Rider normalRider;
    private Rider selfMatchingRider;

    @BeforeEach
    void setUp() {
        // Clear old database records to avoid test collisions
        activeShipmentRepository.deleteAll();
        gearScanRepository.deleteAll();
        vehicleConfigRepository.deleteAll();
        orderRepository.deleteAll();
        riderRepository.deleteAll();
        customerRepository.deleteAll();

        // Seed Vehicle Configs
        vehicleConfigRepository.save(ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.VehicleConfigEntity.builder()
                .vehicleType("E-Bike")
                .maxWeightKg(new BigDecimal("10.00"))
                .averageSpeedKmh(new BigDecimal("20.00"))
                .build());

        vehicleConfigRepository.save(ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.VehicleConfigEntity.builder()
                .vehicleType("Scooter")
                .maxWeightKg(new BigDecimal("25.00"))
                .averageSpeedKmh(new BigDecimal("40.00"))
                .build());

        // 1. Seed Customer
        customer = Customer.builder()
                .customerId("CUST-100")
                .fullName("Alice Smith")
                .email("alice@starfleet.org")
                .hashedEmail("alice_hash_100")
                .walletBalance(new BigDecimal("100.00"))
                .loyaltyPoints(0)
                .build();
        customerRepository.save(customer);

        // 2. Seed Order
        order = Order.builder()
                .customer(customer)
                .totalAmount(new BigDecimal("30.00"))
                .paymentMethod("Wallet")
                .status("pending")
                .idempotencyKey("ORDER-KEY-DISP-1")
                .build();
        order = orderPort.save(order);

        // 3. Seed Normal Rider (E-Bike, Max Weight 10.0kg)
        ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity normalRiderEntity = ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity.builder()
                .riderId("RIDER-EBike-1")
                .fullName("Bob Miller")
                .vehicleType("E-Bike")
                .onboardingStatus("active")
                .walletBalance(BigDecimal.ZERO)
                .trustScore(100)
                .build();
        riderRepository.save(normalRiderEntity);
        normalRider = ch.swissqcommerce.backend.domain.enrollment.core.model.Rider.builder().riderId("RIDER-EBike-1").build();

        // 4. Seed Self-Matching Rider (Name matches Customer)
        ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity selfMatchingRiderEntity = ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity.builder()
                .riderId("RIDER-SelfMatch-1")
                .fullName("Alice Smith") // Exact match with customer name
                .vehicleType("Scooter")
                .onboardingStatus("active")
                .walletBalance(BigDecimal.ZERO)
                .trustScore(100)
                .build();
        riderRepository.save(selfMatchingRiderEntity);
        selfMatchingRider = ch.swissqcommerce.backend.domain.enrollment.core.model.Rider.builder().riderId("RIDER-SelfMatch-1").build();
    }

    @Test
    public void testAssignOrder_FailsWithoutPassedGearScan() {
        // Act & Assert (Should fail since normalRider has no passed gear scans in the last 24h)
        assertThrows(IllegalStateException.class, () -> {
            dispatchUseCase.assignOrder(order.getOrderId(), normalRider.getRiderId(), new BigDecimal("5.00"));
        });
    }

    @Test
    public void testAssignOrder_FailsWhenWeightExceedsCapacity() {
        // Given - Submit valid gear scan
        dispatchUseCase.submitGearScan(normalRider.getRiderId(), "THERMAL_BAG", "PASSED", "http://image.url/bag.jpg");

        // Act & Assert (Weight of 15.00kg exceeds E-Bike limit of 10.00kg)
        assertThrows(IllegalStateException.class, () -> {
            dispatchUseCase.assignOrder(order.getOrderId(), normalRider.getRiderId(), new BigDecimal("15.00"));
        });
    }

    @Test
    public void testAssignOrder_FailsOnSelfMatching() {
        // Given - Submit valid gear scan for the self-matching rider
        dispatchUseCase.submitGearScan(selfMatchingRider.getRiderId(), "THERMAL_BAG", "PASSED", "http://image.url/bag.jpg");

        // Act & Assert (Rider name matches customer name "Alice Smith", triggering fraud block)
        assertThrows(IllegalStateException.class, () -> {
            dispatchUseCase.assignOrder(order.getOrderId(), selfMatchingRider.getRiderId(), new BigDecimal("5.00"));
        });
    }

    @Test
    public void testAssignOrder_SucceedsUnderNormalConditions() {
        // Given - Submit gear scan and normal parameters
        dispatchUseCase.submitGearScan(normalRider.getRiderId(), "THERMAL_BAG", "PASSED", "http://image.url/bag.jpg");

        // When
        ActiveShipment shipment = dispatchUseCase.assignOrder(
                order.getOrderId(), normalRider.getRiderId(), new BigDecimal("5.00")
        );

        // Then
        assertNotNull(shipment);
        assertEquals("ASSIGNED", shipment.getStatus().name());
        assertEquals(normalRider.getRiderId(), shipment.getRiderId());
        
        Order updatedOrder = orderPort.findById(order.getOrderId()).orElseThrow();
        assertEquals(normalRider.getRiderId(), updatedOrder.getRider().getRiderId());
    }

    @Test
    public void testStationaryGPSReallocation() {
        // Given - Assign normal order
        dispatchUseCase.submitGearScan(normalRider.getRiderId(), "THERMAL_BAG", "PASSED", "http://image.url/bag.jpg");
        ActiveShipment shipment = dispatchUseCase.assignOrder(
                order.getOrderId(), normalRider.getRiderId(), new BigDecimal("5.00")
        );

        // When - Send first GPS update
        dispatchUseCase.updateRiderGps(normalRider.getRiderId(), new BigDecimal("47.3769"), new BigDecimal("8.5417"));
        
        // Send second GPS update to the exact same position (flags as stationary)
        shipment = dispatchUseCase.updateRiderGps(normalRider.getRiderId(), new BigDecimal("47.3769"), new BigDecimal("8.5417"));
        assertNotNull(shipment.getStationarySince());

        // Manually adjust the stationarySince timestamp back in database by 11 minutes (simulating time elapsed)
        ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.ActiveShipmentEntity shipmentEntity = activeShipmentRepository.findById(shipment.getShipmentId()).orElseThrow();
        shipmentEntity.setStationarySince(OffsetDateTime.now().minusMinutes(11));
        activeShipmentRepository.saveAndFlush(shipmentEntity);

        // Act - Run reallocation audit
        List<Integer> reallocated = dispatchUseCase.runReallocationAudit();

        // Then
        assertEquals(1, reallocated.size());
        assertEquals(order.getOrderId(), reallocated.get(0));

        ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence.ActiveShipmentEntity updatedShipment = activeShipmentRepository.findById(shipment.getShipmentId()).orElseThrow();
        assertEquals("REALLOCATED", updatedShipment.getStatus());

        Order resetOrder = orderPort.findById(order.getOrderId()).orElseThrow();
        assertEquals("pending", resetOrder.getStatus());
        assertNull(resetOrder.getRider());
    }
}
