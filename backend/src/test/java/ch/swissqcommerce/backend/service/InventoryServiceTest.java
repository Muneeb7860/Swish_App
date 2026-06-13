package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swissqcommerce.backend.domain.inventory.core.service.InventoryService;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InventoryServiceTest {

    private final InventoryService inventoryService = new InventoryService();

    @Test
    public void testGetPickerQueue() {
        List<String> queue = inventoryService.getPickerQueue();
        assertEquals(2, queue.size());
        assertTrue(queue.contains("Order-123-Pick"));
        assertTrue(queue.contains("Order-124-Pick"));
    }

    @Test
    public void testHandoverPicker() {
        String result = inventoryService.handoverPicker("Order-999");
        assertEquals("Handover successful for Order-999", result);
    }

    @Test
    public void testRebalanceInventory() {
        String result = inventoryService.rebalanceInventory();
        assertEquals("Inventory rebalanced successfully", result);
    }

    @Test
    public void testGetCatalog() {
        List<String> catalog = inventoryService.getCatalog();
        assertEquals(3, catalog.size());
        assertTrue(catalog.contains("Product-A"));
        assertTrue(catalog.contains("Product-B"));
        assertTrue(catalog.contains("Product-C"));
    }
}
