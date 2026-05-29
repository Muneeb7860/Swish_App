package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.Inventory;
import java.util.Optional;

public interface InventoryPort {
    Optional<Inventory> findInventoryById(String id);
    Inventory save(Inventory inventory);
}
