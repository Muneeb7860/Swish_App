package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.Inventory;
import java.util.List;
import java.util.Optional;

public interface InventoryPort {
    Optional<Inventory> findInventoryById(String id);

    Inventory save(Inventory inventory);

    /** All stocked catalog items (used by the customer catalog browse endpoint). */
    List<Inventory> findAllInventory();
}
