package ch.swissqcommerce.backend.domain.inventory.adapter.in.web;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.port.in.InventoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryUseCase inventoryUseCase;

    @PostMapping("/{sku}/reserve")
    public ResponseEntity<Void> reserveStock(@PathVariable String sku, @RequestParam int amount) {
        inventoryUseCase.reserveStock(sku, amount);
        return ResponseEntity.ok().build();
    }
}
