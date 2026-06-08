package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.WastageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WastageLogRepository extends JpaRepository<WastageLog, Integer> {
    List<WastageLog> findByStoreStoreId(String storeId);
    List<WastageLog> findByItemItemId(String itemId);
}
