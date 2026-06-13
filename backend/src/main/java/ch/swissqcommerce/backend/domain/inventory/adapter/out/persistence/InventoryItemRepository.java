package ch.swissqcommerce.backend.domain.inventory.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, String> {
    Optional<InventoryItemEntity> findBySku(String sku);
}
