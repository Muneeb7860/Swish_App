package ch.swissqcommerce.backend.domain.inventory.core.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    public List<String> getPickerQueue() {
        return List.of("Order-123-Pick", "Order-124-Pick");
    }

    public String handoverPicker(String orderId) {
        return "Handover successful for " + orderId;
    }

    public String rebalanceInventory() {
        return "Inventory rebalanced successfully";
    }

    @Cacheable("catalog")
    public List<String> getCatalog() {
        return List.of("Product-A", "Product-B", "Product-C");
    }
}
