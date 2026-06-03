package ch.swissqcommerce.backend.domain.inventory.adapter.in.web;

import ch.swissqcommerce.backend.domain.inventory.core.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/picker/queue")
    public List<String> getPickerQueue() {
        return inventoryService.getPickerQueue();
    }

    @PostMapping("/inventory/picker/handover")
    public String handoverPicker(@RequestBody Map<String, String> payload) {
        return inventoryService.handoverPicker(payload.get("orderId"));
    }

    @PostMapping("/inventory/rebalance")
    public String rebalanceInventory() {
        return inventoryService.rebalanceInventory();
    }

    @GetMapping("/customer/catalog")
    public List<String> getCatalog() {
        return inventoryService.getCatalog();
    }
}
