package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.HitlQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HitlQueueRepository extends JpaRepository<HitlQueue, String> {
    List<HitlQueue> findByStatusOrderByCreatedAtDesc(String status);
}
