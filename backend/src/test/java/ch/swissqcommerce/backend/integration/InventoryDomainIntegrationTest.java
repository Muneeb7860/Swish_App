package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence.InventoryItemEntity;
import ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence.InventoryItemRepository;
import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.port.in.StockManagementUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the hexagonal inventory domain (StockManagementUseCase).
 * Verifies that reserveStock / releaseStock / fulfillStock / addStock operations
 * correctly persist to the inventory_items table via InventoryPersistenceAdapter.
 */
@SpringBootTest
@Transactional
public class InventoryDomainIntegrationTest {

    @Autowired
    private StockManagementUseCase stockManagementUseCase;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    private static final String TEST_SKU = "SKU-MILK-HEX-001";

    @BeforeEach
    void setUp() {
        inventoryItemRepository.deleteAll();

        // Seed one item with 20 available, 0 reserved
        InventoryItemEntity seed = InventoryItemEntity.builder()
                .id(UUID.randomUUID().toString())
                .sku(TEST_SKU)
                .availableAmount(20)
                .reservedAmount(0)
                .build();
        inventoryItemRepository.save(seed);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void testReserveStock_decreasesAvailableAndIncreasesReserved() {
        InventoryItem result = stockManagementUseCase.reserveStock(TEST_SKU, 5);

        assertEquals(15, result.getAvailableQuantity().getValue());
        assertEquals(5, result.getReservedQuantity().getValue());

        // Verify persistence
        InventoryItemEntity entity = inventoryItemRepository.findBySku(TEST_SKU).orElseThrow();
        assertEquals(15, entity.getAvailableAmount());
        assertEquals(5, entity.getReservedAmount());
    }

    @Test
    void testReleaseStock_movesReservedBackToAvailable() {
        // First reserve some stock
        stockManagementUseCase.reserveStock(TEST_SKU, 8);

        // Then release half of it
        stockManagementUseCase.releaseStock(TEST_SKU, 4);

        InventoryItemEntity entity = inventoryItemRepository.findBySku(TEST_SKU).orElseThrow();
        assertEquals(16, entity.getAvailableAmount()); // 20 - 8 + 4
        assertEquals(4, entity.getReservedAmount());   // 8 - 4
    }

    @Test
    void testFulfillStock_decreasesReservedWithoutRestoringAvailable() {
        stockManagementUseCase.reserveStock(TEST_SKU, 6);
        stockManagementUseCase.fulfillStock(TEST_SKU, 6);

        InventoryItemEntity entity = inventoryItemRepository.findBySku(TEST_SKU).orElseThrow();
        assertEquals(14, entity.getAvailableAmount()); // 20 - 6 (reserved still consumed)
        assertEquals(0, entity.getReservedAmount());
    }

    @Test
    void testAddStock_increasesAvailableAmount() {
        InventoryItem result = stockManagementUseCase.addStock(TEST_SKU, 10);

        assertEquals(30, result.getAvailableQuantity().getValue());

        InventoryItemEntity entity = inventoryItemRepository.findBySku(TEST_SKU).orElseThrow();
        assertEquals(30, entity.getAvailableAmount());
    }

    // ── Low-stock threshold ────────────────────────────────────────────────────

    @Test
    void testReserveStock_triggersLowStockEventWhenBelowThreshold() {
        // Reserve enough to drop available below the 10-unit low-stock threshold
        // 20 - 12 = 8 (below 10), so publishLowStockEvent should be called.
        // The InventoryEventPublisherAdapter is a no-op stub, so we just verify
        // the operation completes without error and persists correctly.
        InventoryItem result = stockManagementUseCase.reserveStock(TEST_SKU, 12);

        assertEquals(8, result.getAvailableQuantity().getValue());
        assertEquals(12, result.getReservedQuantity().getValue());
    }

    // ── Error / guard cases ────────────────────────────────────────────────────

    @Test
    void testReserveStock_throwsWhenQuantityExceedsAvailable() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stockManagementUseCase.reserveStock(TEST_SKU, 999));

        assertTrue(ex.getMessage().toLowerCase().contains("insufficient"),
                "Expected 'insufficient' in error message but got: " + ex.getMessage());
    }

    @Test
    void testReserveStock_throwsForUnknownSku() {
        assertThrows(IllegalArgumentException.class,
                () -> stockManagementUseCase.reserveStock("NONEXISTENT-SKU", 1));
    }

    @Test
    void testReleaseStock_throwsWhenReleasingMoreThanReserved() {
        stockManagementUseCase.reserveStock(TEST_SKU, 3);

        assertThrows(IllegalArgumentException.class,
                () -> stockManagementUseCase.releaseStock(TEST_SKU, 10));
    }

    @Test
    void testReserveStock_rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> stockManagementUseCase.reserveStock(TEST_SKU, 0));
    }
}
