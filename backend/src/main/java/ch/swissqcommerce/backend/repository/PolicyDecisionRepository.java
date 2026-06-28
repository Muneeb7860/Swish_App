package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.PolicyDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyDecisionRepository extends JpaRepository<PolicyDecision, Long> {
    List<PolicyDecision> findBySuggestionIdOrderByCreatedAtDesc(UUID suggestionId);

    List<PolicyDecision> findBySuggestionIdOrderByIdDesc(UUID suggestionId);
}
