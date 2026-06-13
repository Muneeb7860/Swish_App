package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.HitlQueue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HitlQueueRepository extends JpaRepository<HitlQueue, String> {
    List<HitlQueue> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);
}
