package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    List<Inventory> findByStoreStoreId(String storeId);
}
