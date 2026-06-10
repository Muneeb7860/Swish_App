package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.ChaosFaultLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChaosFaultLogRepository extends JpaRepository<ChaosFaultLog, Integer> {
    List<ChaosFaultLog> findByResolvedAtIsNull();
    long countByResolvedAtIsNull();
}
