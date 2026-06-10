package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.core.model.ShipmentStatus;
import ch.swissqcommerce.backend.domain.dispatch.core.service.DispatchServiceImpl;
import ch.swissqcommerce.backend.domain.dispatch.port.out.DispatchPort;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DispatchServiceImplTest {

    @Mock
    private DispatchPort dispatchPort;

    @Mock
    private EnrollmentOutPort enrollmentOutPort;

    @Mock
    private OrderPort orderPort;

    @InjectMocks
    private DispatchServiceImpl dispatchService;

    private Rider mockRider;
    private Order mockOrder;

    @BeforeEach
    public void setUp() {
        mockRider = new Rider();
        mockRider.setRiderId("RIDER-1");
        mockRider.setFullName("John Rider");
        mockRider.setVehicleType("BICYCLE");

        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");
        customer.setFullName("Jane Customer");

        mockOrder = new Order();
        mockOrder.setOrderId(100);
        mockOrder.setCustomer(customer);
    }

    @Test
    public void testSubmitGearScan() {
        when(enrollmentOutPort.findRiderById("RIDER-1")).thenReturn(Optional.of(mockRider));
        when(dispatchPort.saveGearScan(any(GearScan.class))).thenAnswer(i -> i.getArguments()[0]);

        GearScan scan = dispatchService.submitGearScan("RIDER-1", "HELMET", "PASSED", "url");

        assertNotNull(scan);
        assertEquals("RIDER-1", scan.getRiderId());
        assertEquals("HELMET", scan.getGearType());
        verify(dispatchPort).saveGearScan(any(GearScan.class));
    }

    @Test
    public void testUpdateRiderGps() {
        when(enrollmentOutPort.findRiderById("RIDER-1")).thenReturn(Optional.of(mockRider));
        
        ActiveShipment shipment = ActiveShipment.builder().shipmentId("S1").build();
        when(dispatchPort.findActiveShipmentsByRiderAndStatus("RIDER-1", "ASSIGNED")).thenReturn(List.of(shipment));
        when(dispatchPort.saveActiveShipment(any(ActiveShipment.class))).thenAnswer(i -> i.getArguments()[0]);

        ActiveShipment updated = dispatchService.updateRiderGps("RIDER-1", BigDecimal.valueOf(47.0), BigDecimal.valueOf(8.0));

        assertNotNull(updated);
        assertEquals(BigDecimal.valueOf(47.0), updated.getLastLat());
    }

    @Test
    public void testAssignOrder() {
        when(enrollmentOutPort.findRiderById("RIDER-1")).thenReturn(Optional.of(mockRider));
        when(orderPort.findById(100)).thenReturn(Optional.of(mockOrder));
        when(dispatchPort.isRiderEligible(any())).thenReturn(true);
        when(dispatchPort.findActiveShipmentByOrder(100)).thenReturn(Optional.empty());
        when(dispatchPort.saveActiveShipment(any(ActiveShipment.class))).thenAnswer(i -> i.getArguments()[0]);

        ActiveShipment shipment = dispatchService.assignOrder(100, "RIDER-1", BigDecimal.TEN);

        assertNotNull(shipment);
        assertEquals("RIDER-1", shipment.getRiderId());
        assertEquals(ShipmentStatus.ASSIGNED, shipment.getStatus());
        verify(orderPort).save(mockOrder);
    }

    @Test
    public void testAssignOrder_RiderNotFound() {
        when(enrollmentOutPort.findRiderById("INVALID-RIDER")).thenReturn(Optional.empty());
        
        assertThrows(java.util.NoSuchElementException.class, () -> 
            dispatchService.assignOrder(100, "INVALID-RIDER", BigDecimal.TEN));
        
        verify(dispatchPort, never()).saveActiveShipment(any());
    }

    @Test
    public void testAssignOrder_RiderNotEligible() {
        when(enrollmentOutPort.findRiderById("RIDER-1")).thenReturn(Optional.of(mockRider));
        when(orderPort.findById(100)).thenReturn(Optional.of(mockOrder));
        when(dispatchPort.isRiderEligible(any())).thenReturn(false);
        
        assertThrows(IllegalStateException.class, () -> 
            dispatchService.assignOrder(100, "RIDER-1", BigDecimal.TEN));
            
        verify(dispatchPort, never()).saveActiveShipment(any());
    }

    @Test
    public void testUpdateShipmentStatus() {
        ActiveShipment shipment = ActiveShipment.builder().shipmentId("S1").build();
        when(dispatchPort.findActiveShipmentByOrder(100)).thenReturn(Optional.of(shipment));
        when(dispatchPort.saveActiveShipment(any())).thenAnswer(i -> i.getArguments()[0]);

        ActiveShipment updated = dispatchService.updateShipmentStatus(100, "DELIVERING");

        assertNotNull(updated);
        assertEquals(ShipmentStatus.DELIVERING, updated.getStatus());
    }

    @Test
    public void testRunReallocationAudit() {
        ActiveShipment oldShipment = ActiveShipment.builder()
                .shipmentId("S1")
                .orderId(100)
                .stationarySince(OffsetDateTime.now().minusMinutes(20))
                .status(ShipmentStatus.ASSIGNED)
                .build();
                
        when(dispatchPort.findActiveShipmentsByStatusIn(anyList())).thenReturn(List.of(oldShipment));
        when(orderPort.findAllById(anyList())).thenReturn(List.of(mockOrder));

        List<Integer> reallocated = dispatchService.runReallocationAudit();

        assertEquals(1, reallocated.size());
        assertEquals(100, reallocated.get(0));
        assertEquals(ShipmentStatus.REALLOCATED, oldShipment.getStatus());
    }
}
