package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.PolicyDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyDecisionRepository extends JpaRepository<PolicyDecision, Long> {
}
