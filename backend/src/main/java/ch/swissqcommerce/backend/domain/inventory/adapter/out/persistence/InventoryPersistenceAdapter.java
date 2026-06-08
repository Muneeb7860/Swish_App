package ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.inventory.core.model.InventoryItem;
import ch.swissqcommerce.backend.domain.inventory.core.model.SKU;
import ch.swissqcommerce.backend.domain.inventory.port.out.InventoryRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    @Override
    public InventoryItem save(InventoryItem item) {
        return item;
    }

    @Override
    public Optional<InventoryItem> findBySku(SKU sku) {
        return Optional.empty();
    }
}
