package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.ChaosFaultLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChaosFaultLogRepository extends JpaRepository<ChaosFaultLog, Integer> {
    List<ChaosFaultLog> findByResolvedAtIsNull();

    long countByResolvedAtIsNull();
}
