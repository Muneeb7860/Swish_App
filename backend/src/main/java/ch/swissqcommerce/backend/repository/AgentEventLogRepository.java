package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.AgentEventLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentEventLogRepository extends JpaRepository<AgentEventLog, Long> {

    List<AgentEventLog> findTop50ByOrderByCreatedAtDesc();

    List<AgentEventLog> findByDomainOrderByCreatedAtDesc(String domain);

    List<AgentEventLog> findByPolicyStatusOrderByCreatedAtDesc(String policyStatus);

    long countByPolicyStatus(String policyStatus);

    List<AgentEventLog> findByAgentAndDomainAndPolicyStatusAndExecutedOrderByCreatedAtDesc(
            String agent, String domain, String policyStatus, Boolean executed);
}
