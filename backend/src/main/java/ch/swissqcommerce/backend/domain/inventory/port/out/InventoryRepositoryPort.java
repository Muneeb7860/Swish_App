package ch.swissqcommerce.backend.domain.inventory.port.out;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.core.model.SKU;
import java.util.Optional;

public interface InventoryRepositoryPort {
    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> findBySku(SKU sku);
}
