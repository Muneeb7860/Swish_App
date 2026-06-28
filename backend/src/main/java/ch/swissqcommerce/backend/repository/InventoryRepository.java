package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    List<Inventory> findByStoreStoreId(String storeId);

    @Modifying
    @Query(
            "UPDATE Inventory i SET i.stock = :newStock WHERE i.itemId = :itemId AND i.stock ="
                    + " :oldStock")
    int updateStockOptimistically(
            @Param("itemId") String itemId,
            @Param("oldStock") Integer oldStock,
            @Param("newStock") Integer newStock);
}
