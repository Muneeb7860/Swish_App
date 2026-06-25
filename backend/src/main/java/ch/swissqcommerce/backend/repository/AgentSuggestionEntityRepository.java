package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSuggestionEntityRepository
        extends JpaRepository<AgentSuggestionEntity, UUID> {

    List<AgentSuggestionEntity> findByAgentNameAndDomainAndStatusOrderByCreatedAtDesc(
            String agentName, String domain, String status);

    List<AgentSuggestionEntity> findByStatusOrderByCreatedAtDesc(String status);

    org.springframework.data.domain.Page<AgentSuggestionEntity> findByStatusAndDomain(
            String status, String domain, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<AgentSuggestionEntity> findByStatus(
            String status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(s) FROM AgentSuggestionEntity s "
                    + "WHERE s.domain = :domain AND s.expiresAt < :now "
                    + "AND s.status IN ('pending', 'expired')")
    long countSlaBreachByDomainAndNow(
            @org.springframework.data.repository.query.Param("domain") String domain,
            @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
}
