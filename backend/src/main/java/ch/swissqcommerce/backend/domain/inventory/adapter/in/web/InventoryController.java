package ch.swissqcommerce.backend.domain.inventory.adapter.in.web;

import ch.swissqcommerce.backend.domain.inventory.port.in.StockManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final StockManagementUseCase stockManagementUseCase;

    @PostMapping("/{sku}/reserve")
    public ResponseEntity<Void> reserveStock(@PathVariable String sku, @RequestParam int amount) {
        stockManagementUseCase.reserveStock(sku, amount);
        return ResponseEntity.ok().build();
    }
}
