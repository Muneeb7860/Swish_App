package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSuggestionEntityRepository extends JpaRepository<AgentSuggestionEntity, UUID> {

    List<AgentSuggestionEntity> findByAgentNameAndDomainAndStatusOrderByCreatedAtDesc(
            String agentName, String domain, String status);

    List<AgentSuggestionEntity> findByStatusOrderByCreatedAtDesc(String status);

    org.springframework.data.domain.Page<AgentSuggestionEntity> findByStatusAndDomain(
            String status, String domain, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<AgentSuggestionEntity> findByStatus(
            String status, org.springframework.data.domain.Pageable pageable);
}
