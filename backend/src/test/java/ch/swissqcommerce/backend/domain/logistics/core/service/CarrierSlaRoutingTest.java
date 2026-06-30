package ch.swissqcommerce.backend.domain.logistics.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.CarrierSlaPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.CarrierSlaPort.CarrierSlaData;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
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
public class CarrierSlaRoutingTest {

    @Mock private DarkStoreRepository darkStoreRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private LogisticsDataPort logisticsDataPort;
    @Mock private CarrierSlaPort carrierSlaPort;

    private WarehouseSelectionService service;
    private DarkStore store1;
    private CustomerAddress address;
    private List<CarrierSlaData> mockSlas;

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
                        .dailyOrderCapacity(100)
                        .build();

        address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352"))
                        .build();

        // Setup mock carrier SLAs
        mockSlas =
                Arrays.asList(
                        new CarrierSlaData(
                                "USPS",
                                new BigDecimal("31.75"),
                                5,
                                2,
                                false), // standard standard, no fragile
                        new CarrierSlaData(
                                "UPS",
                                new BigDecimal("68.04"),
                                5,
                                1,
                                true) // express/heavy/fragile OK
                        );

        when(darkStoreRepo.findAll()).thenReturn(List.of(store1));
        when(logisticsDataPort.getTodayOrderCountForWarehouse("WH-NY-01")).thenReturn(10);
    }

    @Test
    public void testCarrierExcludedByWeightLimit() {
        // Mock SLA return
        when(carrierSlaPort.findActiveSlas()).thenReturn(mockSlas);

        // Order item weighs 35kg (exceeds USPS 31.75 limit)
        RoutingOrderData.OrderItem heavyItem =
                new RoutingOrderData.OrderItem("item-heavy", 1, new BigDecimal("35.0"), false);
        RoutingOrderData order = new RoutingOrderData(101, address, null, List.of(heavyItem), 5);

        // Stock exists in WH-NY-01
        Inventory inv = Inventory.builder().itemId("item-heavy").stock(10).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(inv));

        // When requesting rates, USPS is not preferred due to SLA check
        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(order);

        assertTrue(resultOpt.isPresent());
        // Since weight exceeds USPS limit (31.75), carrier must fall back to UPS
        assertEquals("UPS", resultOpt.get().carrier());
    }

    @Test
    public void testCarrierExcludedByFragileItemConstraint() {
        when(carrierSlaPort.findActiveSlas()).thenReturn(mockSlas);

        // Order contains a fragile item
        RoutingOrderData.OrderItem fragileItem =
                new RoutingOrderData.OrderItem("item-fragile", 1, new BigDecimal("1.0"), true);
        RoutingOrderData order = new RoutingOrderData(102, address, null, List.of(fragileItem), 5);

        Inventory inv = Inventory.builder().itemId("item-fragile").stock(10).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(inv));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(order);

        assertTrue(resultOpt.isPresent());
        // USPS does not support fragile, so UPS must be selected
        assertEquals("UPS", resultOpt.get().carrier());
    }

    @Test
    public void testCarrierExcludedByDeliveryWindowConstraint() {
        when(carrierSlaPort.findActiveSlas()).thenReturn(mockSlas);

        // Order requests 1 day delivery
        RoutingOrderData.OrderItem item =
                new RoutingOrderData.OrderItem("item-normal", 1, new BigDecimal("1.0"), false);
        RoutingOrderData order = new RoutingOrderData(103, address, null, List.of(item), 1);

        Inventory inv = Inventory.builder().itemId("item-normal").stock(10).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(inv));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(order);

        assertTrue(resultOpt.isPresent());
        // USPS has express_days = 2, so it cannot meet the 1-day SLA. UPS has express_days = 1.
        assertEquals("UPS", resultOpt.get().carrier());
        assertEquals(1, resultOpt.get().estimatedDeliveryDays());
    }

    @Test
    public void testMultiPackageSplitCalculation() {
        when(carrierSlaPort.findActiveSlas()).thenReturn(mockSlas);

        // Heavy item with total weight 70kg (split into package size of 30kg)
        // 70 / 30 = 3 packages
        RoutingOrderData.OrderItem item =
                new RoutingOrderData.OrderItem("item-normal", 2, new BigDecimal("35.0"), false);
        RoutingOrderData order = new RoutingOrderData(104, address, null, List.of(item), 5);

        Inventory inv = Inventory.builder().itemId("item-normal").stock(10).reservedQty(0).build();
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(inv));

        Optional<WarehouseSelectionUseCase.RoutingResult> resultOpt =
                service.findOptimalWarehouse(order);

        assertTrue(resultOpt.isPresent());
        assertEquals(3, resultOpt.get().packageCount());
    }
}
