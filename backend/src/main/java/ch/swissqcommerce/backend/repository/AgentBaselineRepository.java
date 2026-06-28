package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.AgentBaseline;
import ch.swissqcommerce.backend.model.AgentBaselineId;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentBaselineRepository extends JpaRepository<AgentBaseline, AgentBaselineId> {
    Optional<AgentBaseline> findBySkuAndDate(String sku, LocalDate date);

    @Query("SELECT MAX(b.date) FROM AgentBaseline b")
    LocalDate findMaxDate();
}
