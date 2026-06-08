package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Integer> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
    long countByEventTypeAndStatus(String eventType, String status);
}
