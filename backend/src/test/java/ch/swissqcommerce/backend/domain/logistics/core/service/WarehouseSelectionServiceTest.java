package ch.swissqcommerce.backend.domain.logistics.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.BaselineCost;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.RegionPreference;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WarehouseSelectionServiceTest {

    @Mock private DarkStoreRepository darkStoreRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private LogisticsDataPort logisticsDataPort;

    @Mock
    private ch.swissqcommerce.backend.domain.logistics.core.port.out.CarrierSlaPort carrierSlaPort;

    private WarehouseSelectionService service;

    private DarkStore store1;
    private DarkStore store2;
    private RoutingOrderData orderData;
    private CustomerAddress address;

    @BeforeEach
    public void setUp() {
        service =
                new WarehouseSelectionService(
                        darkStoreRepo, inventoryRepo, logisticsDataPort, carrierSlaPort);

        store1 =
                DarkStore.builder()
                        .storeId("WH-NY-01")
                        .storeName("New York Store")
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .build();

        store2 =
                DarkStore.builder()
                        .storeId("WH-CA-02")
                        .storeName("California Store")
                        .latitude(new BigDecimal("34.0522"))
                        .longitude(new BigDecimal("-118.2437"))
                        .build();

        when(darkStoreRepo.findAll()).thenReturn(List.of(store1, store2));

        address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352")) // Near NY
                        .build();

        orderData =
                new RoutingOrderData(
                        101,
                        address,
                        null, // Original store not needed for selection tests
                        List.of(new RoutingOrderData.OrderItem("item-1", 2)));
    }

    @Test
    public void testFindOptimalWarehouse_StockFilterAndScoring() {
        Inventory stock1 = Inventory.builder().itemId("item-1").stock(5).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(stock1));
        when(inventoryRepo.findByStoreStoreId("WH-CA-02")).thenReturn(Collections.emptyList());

        BaselineCost baseline = new BaselineCost("WH-NY-01", new BigDecimal("5.50"), 10);
        when(logisticsDataPort.findBaselinesByZipPrefix("800")).thenReturn(List.of(baseline));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(orderData);

        assertTrue(resultOpt.isPresent());
        WarehouseSelectionUseCase.RoutingResult result = resultOpt.get();
        assertEquals("WH-NY-01", result.primaryWarehouseId());
        assertFalse(result.splitShipment());
        assertTrue(result.estimatedCost() > 5.50);
        assertEquals("USPS", result.carrier());
    }

    @Test
    public void testFindOptimalWarehouse_ConfidencePenalty() {
        Inventory stock1 = Inventory.builder().itemId("item-1").stock(5).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(stock1));

        BaselineCost baseline = new BaselineCost("WH-NY-01", new BigDecimal("10.00"), 3);
        when(logisticsDataPort.findBaselinesByZipPrefix("800")).thenReturn(List.of(baseline));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(orderData);

        assertTrue(resultOpt.isPresent());
        WarehouseSelectionUseCase.RoutingResult result = resultOpt.get();
        assertTrue(result.estimatedCost() > 12.00);
    }

    @Test
    public void testFindOptimalWarehouse_SplitShipmentFallback() {
        Inventory stockNY = Inventory.builder().itemId("item-1").stock(5).reservedQty(0).build();
        Inventory stockCA = Inventory.builder().itemId("item-2").stock(10).reservedQty(0).build();

        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(stockNY));
        when(inventoryRepo.findByStoreStoreId("WH-CA-02")).thenReturn(List.of(stockCA));

        // Create new OrderData for split shipment
        RoutingOrderData splitOrderData =
                new RoutingOrderData(
                        101,
                        address,
                        null,
                        List.of(
                                new RoutingOrderData.OrderItem("item-1", 2),
                                new RoutingOrderData.OrderItem("item-2", 1)));

        RegionPreference pref = new RegionPreference("800", "WH-NY-01", "WH-CA-02");
        when(logisticsDataPort.findRegionPref("800")).thenReturn(Optional.of(pref));
        when(logisticsDataPort.findBaselinesByZipPrefix("800")).thenReturn(Collections.emptyList());
        when(darkStoreRepo.findById("WH-NY-01")).thenReturn(Optional.of(store1));
        when(darkStoreRepo.findById("WH-CA-02")).thenReturn(Optional.of(store2));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(splitOrderData);

        assertTrue(resultOpt.isPresent());
        WarehouseSelectionUseCase.RoutingResult result = resultOpt.get();
        assertEquals("WH-NY-01", result.primaryWarehouseId());
        assertTrue(result.splitShipment());
        assertEquals(2, result.splits().size());
        assertEquals("UPS", result.carrier());
    }
}
