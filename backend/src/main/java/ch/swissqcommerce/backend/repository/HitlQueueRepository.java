package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.HitlQueue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface HitlQueueRepository extends JpaRepository<HitlQueue, String> {
    List<HitlQueue> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    @Query("SELECT COUNT(h) FROM HitlQueue h WHERE h.status = :status AND h.type = :type")
    long countByStatusAndType(@Param("status") String status, @Param("type") String type);
}
