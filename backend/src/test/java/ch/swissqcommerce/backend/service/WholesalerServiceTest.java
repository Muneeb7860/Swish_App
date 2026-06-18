
package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.core.service.WholesalerServiceImpl;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.NoSuchElementException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WholesalerServiceTest {

    @Mock private WholesalerPort wholesalerPort;
    @Mock private B2BRestockOrderPort restockOrderPort;
    @Mock private LedgerUseCase ledgerService;
    @Mock private ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort sensorPort;
    @Mock private ch.swissqcommerce.backend.repository.DarkStoreRepository darkStoreRepository;

    @InjectMocks private WholesalerServiceImpl wholesalerService;

    @Test
    public void testCreateRestockOrder_AcademyDiscount() {
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setIsActive(true);
        w.setTrustScore(80);
        w.setBaseInvoiceAmount(new BigDecimal("1000.00"));
        w.setAcademyDiscountActive(true);

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(w));
        when(restockOrderPort.save(any())).thenAnswer(i -> i.getArguments()[0]);

        B2BRestockOrder order = wholesalerService.createRestockOrder("STORE-1", "W-1", null);

        assertNotNull(order);
        assertEquals(0, new BigDecimal("900.00").compareTo(order.getInvoiceAmount()));
        assertFalse(order.getIsFallback());
    }

    @Test
    public void testFulfillRestock() {
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setName("Test W");

        B2BRestockOrder order = new B2BRestockOrder();
        order.setStatus("pending");
        order.setWholesaler(w);
        order.setInvoiceAmount(new BigDecimal("900.00"));

        when(restockOrderPort.findById(1)).thenReturn(Optional.of(order));

        Map<String, Object> res = wholesalerService.fulfillRestock(1);

        assertEquals("fulfilled", order.getStatus());
        verify(ledgerService, times(1)).recordTransaction(anyString(), anyString(), anyList());
        assertEquals("fulfilled", res.get("status"));
    }

    @Test
    public void testCreateRestockOrder_IdempotencyKeyFound() {
        B2BRestockOrder existingOrder = new B2BRestockOrder();
        existingOrder.setIdempotencyKey("key-123");
        
        when(restockOrderPort.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existingOrder));
        
        B2BRestockOrder result = wholesalerService.createRestockOrder("STORE-1", "W-1", "key-123");
        
        assertNotNull(result);
        assertEquals("key-123", result.getIdempotencyKey());
        verify(wholesalerPort, never()).findById(anyString());
    }

    @Test
    public void testCreateRestockOrder_PreferredWholesalerFallbackLowTrust() {
        Wholesaler preferred = new Wholesaler();
        preferred.setWholesalerId("W-1");
        preferred.setIsActive(true);
        preferred.setTrustScore(50); // Low trust (<60)
        preferred.setBaseInvoiceAmount(new BigDecimal("1000.00"));
        preferred.setAcademyDiscountActive(false);

        Wholesaler fallback = new Wholesaler();
        fallback.setWholesalerId("W-2");
        fallback.setIsActive(true);
        fallback.setTrustScore(75);
        fallback.setFallbackInvoiceAmount(new BigDecimal("1200.00"));
        fallback.setAcademyDiscountActive(false);

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(preferred));
        when(wholesalerPort.findAll()).thenReturn(List.of(preferred, fallback));
        when(restockOrderPort.save(any())).thenAnswer(i -> i.getArguments()[0]);

        B2BRestockOrder result = wholesalerService.createRestockOrder("STORE-1", "W-1", null);

        assertNotNull(result);
        assertTrue(result.getIsFallback());
        assertEquals("W-2", result.getWholesaler().getWholesalerId());
        assertEquals(0, new BigDecimal("1200.00").compareTo(result.getInvoiceAmount()));
    }

    @Test
    public void testCreateRestockOrder_PreferredWholesalerFallbackInactive() {
        Wholesaler preferred = new Wholesaler();
        preferred.setWholesalerId("W-1");
        preferred.setIsActive(false); // Inactive
        preferred.setTrustScore(80);
        preferred.setBaseInvoiceAmount(new BigDecimal("1000.00"));
        preferred.setAcademyDiscountActive(false);

        Wholesaler fallback = new Wholesaler();
        fallback.setWholesalerId("W-2");
        fallback.setIsActive(true);
        fallback.setTrustScore(85);
        fallback.setFallbackInvoiceAmount(new BigDecimal("1200.00"));
        fallback.setAcademyDiscountActive(false);

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(preferred));
        when(wholesalerPort.findAll()).thenReturn(List.of(preferred, fallback));
        when(restockOrderPort.save(any())).thenAnswer(i -> i.getArguments()[0]);

        B2BRestockOrder result = wholesalerService.createRestockOrder("STORE-1", "W-1", null);

        assertNotNull(result);
        assertTrue(result.getIsFallback());
        assertEquals("W-2", result.getWholesaler().getWholesalerId());
    }

    @Test
    public void testCreateRestockOrder_NoEligibleWholesaler() {
        Wholesaler preferred = new Wholesaler();
        preferred.setWholesalerId("W-1");
        preferred.setIsActive(false); // Inactive

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(preferred));
        when(wholesalerPort.findAll()).thenReturn(List.of(preferred));

        assertThrows(IllegalStateException.class, () -> {
            wholesalerService.createRestockOrder("STORE-1", "W-1", null);
        });
    }

    @Test
    public void testFulfillRestock_AlreadyFulfilled() {
        B2BRestockOrder order = new B2BRestockOrder();
        order.setStatus("fulfilled"); // Already fulfilled

        when(restockOrderPort.findById(1)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> {
            wholesalerService.fulfillRestock(1);
        });
    }

    @Test
    public void testGetAssignedRestocks() {
        B2BRestockOrder order = new B2BRestockOrder();
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        
        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(w));
        when(restockOrderPort.findByWholesalerId("W-1")).thenReturn(List.of(order));

        List<B2BRestockOrder> result = wholesalerService.getAssignedRestocks("W-1");
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    public void testGetInvoiceSummary() {
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setName("Wholesaler One");
        w.setAcademyDiscountActive(true);
        w.setTrustScore(80);

        B2BRestockOrder o1 = new B2BRestockOrder();
        o1.setStatus("fulfilled");
        o1.setInvoiceAmount(new BigDecimal("500.00"));

        B2BRestockOrder o2 = new B2BRestockOrder();
        o2.setStatus("pending");
        o2.setInvoiceAmount(new BigDecimal("300.00"));

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(w));
        when(restockOrderPort.findByWholesalerId("W-1")).thenReturn(List.of(o1, o2));

        Map<String, Object> summary = wholesalerService.getInvoiceSummary("W-1");

        assertNotNull(summary);
        assertEquals("W-1", summary.get("wholesalerId"));
        assertEquals("Wholesaler One", summary.get("wholesalerName"));
        assertEquals(new BigDecimal("500.00"), summary.get("totalFulfilledInvoiceAmount"));
        assertEquals(1L, summary.get("pendingOrderCount"));
        assertEquals(2, summary.get("totalOrderCount"));
        assertEquals(true, summary.get("academyDiscountActive"));
        assertEquals(80, summary.get("trustScore"));
    }

    @Test
    public void testCreateRestockOrder_reroutesWhenStoreHasFailedSensors() {
        // Mock a failed sensor on STORE-1
        ch.swissqcommerce.backend.domain.sensor.core.model.Sensor failedSensor = 
                ch.swissqcommerce.backend.domain.sensor.core.model.Sensor.builder()
                        .sensorId("SNS-BAD")
                        .storeId("STORE-1")
                        .calibrationStatus("FAILED")
                        .build();

        // Mock a calibrated sensor on STORE-2
        ch.swissqcommerce.backend.domain.sensor.core.model.Sensor goodSensor = 
                ch.swissqcommerce.backend.domain.sensor.core.model.Sensor.builder()
                        .sensorId("SNS-GOOD")
                        .storeId("STORE-2")
                        .calibrationStatus("CALIBRATED")
                        .build();

        when(sensorPort.findByStoreId("STORE-1")).thenReturn(List.of(failedSensor));
        when(sensorPort.findByStoreId("STORE-2")).thenReturn(List.of(goodSensor));

        // Mock stores
        ch.swissqcommerce.backend.model.DarkStore s1 = ch.swissqcommerce.backend.model.DarkStore.builder()
                .storeId("STORE-1").storeName("Store 1").build();
        ch.swissqcommerce.backend.model.DarkStore s2 = ch.swissqcommerce.backend.model.DarkStore.builder()
                .storeId("STORE-2").storeName("Store 2").build();
        when(darkStoreRepository.findAll()).thenReturn(List.of(s1, s2));

        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setIsActive(true);
        w.setTrustScore(80);
        w.setBaseInvoiceAmount(new BigDecimal("1000.00"));

        when(wholesalerPort.findById("W-1")).thenReturn(Optional.of(w));
        when(restockOrderPort.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // Attempt to create restock for STORE-1 (which is failed)
        B2BRestockOrder order = wholesalerService.createRestockOrder("STORE-1", "W-1", null);

        assertNotNull(order);
        // Assert that the store has been rerouted to STORE-2
        assertEquals("STORE-2", order.getStore().getStoreId());
    }
}
