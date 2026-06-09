package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.telemetry.core.service.TelemetryServiceImpl;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.telemetry.port.out.GeoLocationPort;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.NoSuchElementException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock private TelemetryPort telemetryPort;
    @Mock private OrderRepository orderRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;
    @Mock private LedgerUseCase ledgerUseCase;
    @Mock private GeoLocationPort geoLocationPort;

    @InjectMocks
    private TelemetryServiceImpl telemetryService;

    @Test
    public void testIsThermalBreachActive_NotBreached() {
        // Temperature below 8.0 should not breach
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("7.5"));
        assertFalse(active);
    }

    @Test
    public void testIsThermalBreachActive_BreachedImmediate() {
        // First tick above 8.0 should trigger alert but not breach yet (since duration is 0)
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("9.0"));
        assertFalse(active);
    }

    @Test
    public void testUpdateLocation_Success() {
        boolean result = telemetryService.updateLocation(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"));
        assertTrue(result);
        verify(geoLocationPort).updateLocation(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"));
    }

    @Test
    public void testUpdateLocation_OutlierGPSDiscarded() throws InterruptedException {
        // First location update is successful
        boolean firstResult = telemetryService.updateLocation(2, new BigDecimal("47.3769"), new BigDecimal("8.5417"), new BigDecimal("5.0"));
        assertTrue(firstResult);
        verify(geoLocationPort).updateLocation(2, new BigDecimal("47.3769"), new BigDecimal("8.5417"), new BigDecimal("5.0"));

        // Wait to trigger distance/speed filter
        Thread.sleep(600);

        // Send coordinate 1 degree away (approx 130 km) -> impossible speed for 600ms
        boolean secondResult = telemetryService.updateLocation(2, new BigDecimal("48.3769"), new BigDecimal("9.5417"), new BigDecimal("5.0"));
        
        // Assert that the outlier was detected and discarded (returned false)
        assertFalse(secondResult);
        // Verify thatgeoLocationPort was NEVER called with the second coordinate
        verify(geoLocationPort, never()).updateLocation(2, new BigDecimal("48.3769"), new BigDecimal("9.5417"), new BigDecimal("5.0"));
    }

    @Test
    public void testGetLatestLocation() {
        GeoLocationPort.RiderLocation mockLoc = new GeoLocationPort.RiderLocation(new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"));
        when(geoLocationPort.getLatestLocation(1)).thenReturn(mockLoc);

        GeoLocationPort.RiderLocation result = telemetryService.getLatestLocation(1);
        assertNotNull(result);
        assertEquals(new BigDecimal("47.0"), result.getLatitude());
        assertEquals(new BigDecimal("8.0"), result.getLongitude());
        assertEquals(new BigDecimal("5.0"), result.getTemperature());
    }

    @Test
    public void testRecordTelemetry_Success_NoAlert() {
        Order order = new Order();
        order.setOrderId(1);
        order.setStatus("shipping");

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));
        when(telemetryPort.save(any(OrderTelemetryLog.class))).thenAnswer(i -> i.getArguments()[0]);

        OrderTelemetryLog log = telemetryService.recordTelemetry(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"), false);

        assertNotNull(log);
        assertEquals(order, log.getOrder());
        assertEquals(new BigDecimal("5.0"), log.getTemperature());
        assertFalse(log.getAlertTriggered());
    }

    @Test
    public void testRecordTelemetry_Success_AlertTriggered() {
        Order order = new Order();
        order.setOrderId(1);
        order.setStatus("shipping");

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));
        when(telemetryPort.save(any(OrderTelemetryLog.class))).thenAnswer(i -> i.getArguments()[0]);

        OrderTelemetryLog log = telemetryService.recordTelemetry(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("9.0"), false);

        assertNotNull(log);
        assertTrue(log.getAlertTriggered());
    }

    @Test
    public void testRecordTelemetry_Success_CargoSpoiled() {
        Rider rider = new Rider();
        rider.setRiderId("rider-1");
        rider.setTrustScore(80);

        Order order = new Order();
        order.setOrderId(1);
        order.setStatus("shipping");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRider(rider);

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));
        when(telemetryPort.save(any(OrderTelemetryLog.class))).thenAnswer(i -> i.getArguments()[0]);

        OrderTelemetryLog log = telemetryService.recordTelemetry(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("13.0"), false);

        assertNotNull(log);
        assertEquals("spoiled", order.getStatus());
        assertEquals(50, rider.getTrustScore()); // trust reduced by 30 (80 - 30)
        verify(telemetryPort).saveOrder(order);
        verify(telemetryPort).saveRider(rider);
        verify(telemetryPort).saveTrustLedger(any());
        verify(ledgerUseCase).recordTransaction(eq("COLD-BREACH"), anyString(), anyList());
    }

    @Test
    public void testRecordTelemetry_OrderNotFound() {
        when(telemetryPort.findOrderById(999)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> {
            telemetryService.recordTelemetry(999, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"), false);
        });
    }

    @Test
    public void testInjectDryIce_Success() {
        Rider rider = new Rider();
        rider.setActiveLat(new BigDecimal("47.0"));
        rider.setActiveLng(new BigDecimal("8.0"));

        Order order = new Order();
        order.setOrderId(1);
        order.setStatus("shipping");
        order.setRider(rider);

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));
        when(telemetryPort.save(any(OrderTelemetryLog.class))).thenAnswer(i -> i.getArguments()[0]);

        // Call method under test
        telemetryService.injectDryIce(1);

        verify(ledgerUseCase).recordTransaction(eq("DRY-ICE-DEBIT"), anyString(), anyList());
        // Verify that recordTelemetry was transitively called which resets temp to 4.0 and saves
        verify(telemetryPort).save(any(OrderTelemetryLog.class));
    }

    @Test
    public void testInjectDryIce_IllegalStateNotShipping() {
        Order order = new Order();
        order.setOrderId(1);
        order.setStatus("pending"); // Not shipping

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> {
            telemetryService.injectDryIce(1);
        });
    }

    @Test
    public void testQueueAndFlushTicks() {
        Order order = new Order();
        order.setOrderId(1);

        when(telemetryPort.findOrderById(1)).thenReturn(Optional.of(order));

        telemetryService.queueTick(1, new BigDecimal("47.0"), new BigDecimal("8.0"), new BigDecimal("5.0"), false);
        telemetryService.flushTickBuffer();

        verify(telemetryPort).save(any(OrderTelemetryLog.class));
    }
}
