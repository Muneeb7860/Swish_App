package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.WastageLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WastageLogRepository extends JpaRepository<WastageLog, Integer> {
    List<WastageLog> findByStoreStoreId(String storeId);

    List<WastageLog> findByItemItemId(String itemId);
}
