package ch.swissqcommerce.backend.domain.logistics.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.governance.core.service.OutcomeResult;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.BaselineCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.ShipmentCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LogisticsOutcomeProcessorTest {

    private LogisticsDataPort logisticsDataPort;
    private LogisticsOutcomeProcessor processor;

    @BeforeEach
    public void setUp() {
        logisticsDataPort = mock(LogisticsDataPort.class);
        processor = new LogisticsOutcomeProcessor(logisticsDataPort);
    }

    @Test
    public void testDomain() {
        assertEquals("routing", processor.domain());
    }

    @Test
    public void testEvaluate_SavingsCalc_Success() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("routing")
                        .entityId("order_id=101")
                        .build();

        ExecutionRecord exec = ExecutionRecord.builder().createdAt(OffsetDateTime.now()).build();

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352"))
                        .build();

        DarkStore originalStore =
                DarkStore.builder()
                        .storeId("store-test-1")
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .build();

        RoutingOrderData orderData =
                new RoutingOrderData(
                        101,
                        address,
                        originalStore,
                        List.of(new RoutingOrderData.OrderItem("item-1", 2)));

        BaselineCost baseline = new BaselineCost("store-test-1", new BigDecimal("10.00"), 10);
        ShipmentCost shipment = new ShipmentCost(1L, new BigDecimal("6.50"));

        when(logisticsDataPort.findRoutingOrderData(101)).thenReturn(Optional.of(orderData));
        when(logisticsDataPort.findBaselinesByZipPrefix("800")).thenReturn(List.of(baseline));
        when(logisticsDataPort.findShipmentCostsByOrderId(101)).thenReturn(List.of(shipment));

        OutcomeResult result = processor.evaluate(exec, suggestion);

        assertTrue(result.getSuccess());
        double savings = (double) result.getMetrics().get("shipping_savings_usd");
        assertTrue(savings > 3.0);
        assertTrue(result.getMeasurementWindow().contains(exec.getCreatedAt().toString()));
        assertTrue(
                result.getMeasurementWindow().contains(exec.getCreatedAt().plusDays(3).toString()));
    }

    @Test
    public void testEvaluate_NullActualCost_Handling() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("routing")
                        .entityId("order_id=101")
                        .build();

        ExecutionRecord exec = ExecutionRecord.builder().createdAt(OffsetDateTime.now()).build();

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352"))
                        .build();

        DarkStore originalStore =
                DarkStore.builder()
                        .storeId("store-test-1")
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .build();

        RoutingOrderData orderData =
                new RoutingOrderData(
                        101,
                        address,
                        originalStore,
                        List.of(new RoutingOrderData.OrderItem("item-1", 2)));

        BaselineCost baseline = new BaselineCost("store-test-1", new BigDecimal("10.00"), 10);
        ShipmentCost shipment = new ShipmentCost(1L, null);

        when(logisticsDataPort.findRoutingOrderData(101)).thenReturn(Optional.of(orderData));
        when(logisticsDataPort.findBaselinesByZipPrefix("800")).thenReturn(List.of(baseline));
        when(logisticsDataPort.findShipmentCostsByOrderId(101)).thenReturn(List.of(shipment));

        OutcomeResult result = processor.evaluate(exec, suggestion);

        assertFalse(result.getSuccess());
        double savings = (double) result.getMetrics().get("shipping_savings_usd");
        assertEquals(0.0, savings);
    }
}
