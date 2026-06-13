package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Integer> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    long countByEventTypeAndStatus(String eventType, String status);
}
