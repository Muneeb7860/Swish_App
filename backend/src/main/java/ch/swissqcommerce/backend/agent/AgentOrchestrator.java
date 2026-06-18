package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.gateway.ExecutionGateway;
import ch.swissqcommerce.backend.model.AgentEventLog;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.policy.PolicyDecision;
import ch.swissqcommerce.backend.policy.PolicyEngine;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final OpsAgent opsAgent;
    private final RoutingAgent routingAgent;
    private final PricingAgent pricingAgent;
    private final RiskAgent riskAgent;
    private final SupportAgent supportAgent;
    
    private final PolicyEngine policyEngine;
    private final ExecutionGateway executionGateway;
    private final OutboxEventRepository outboxEventRepo;

    public AgentOrchestrator(
            OpsAgent opsAgent,
            RoutingAgent routingAgent,
            PricingAgent pricingAgent,
            RiskAgent riskAgent,
            SupportAgent supportAgent,
            PolicyEngine policyEngine,
            ExecutionGateway executionGateway,
            OutboxEventRepository outboxEventRepo) {
        this.opsAgent = opsAgent;
        this.routingAgent = routingAgent;
        this.pricingAgent = pricingAgent;
        this.riskAgent = riskAgent;
        this.supportAgent = supportAgent;
        this.policyEngine = policyEngine;
        this.executionGateway = executionGateway;
        this.outboxEventRepo = outboxEventRepo;
    }

    /**
     * Synchronously execute the full agent pipeline for debugging or direct API requests.
     */
    public List<AgentEventLog> runOrchestrationSync(String inputSummary) {
        log.info("AgentOrchestrator: Starting synchronous orchestration. Input: {}", inputSummary);
        List<AgentEventLog> logs = new ArrayList<>();

        // 1. Ops Agent (Inventory)
        try {
            AgentSuggestion suggestion = opsAgent.analyze();
            PolicyDecision decision = policyEngine.evaluate(suggestion);
            logs.add(executionGateway.process("OpsAgent", suggestion, decision, inputSummary));
        } catch (Exception e) {
            log.error("OpsAgent orchestration failed", e);
        }

        // 2. Routing Agent
        try {
            AgentSuggestion suggestion = routingAgent.analyze();
            PolicyDecision decision = policyEngine.evaluate(suggestion);
            logs.add(executionGateway.process("RoutingAgent", suggestion, decision, inputSummary));
        } catch (Exception e) {
            log.error("RoutingAgent orchestration failed", e);
        }

        // 3. Pricing Agent
        try {
            AgentSuggestion suggestion = pricingAgent.analyze();
            PolicyDecision decision = policyEngine.evaluate(suggestion);
            logs.add(executionGateway.process("PricingAgent", suggestion, decision, inputSummary));
        } catch (Exception e) {
            log.error("PricingAgent orchestration failed", e);
        }

        // 4. Risk Agent
        try {
            AgentSuggestion suggestion = riskAgent.analyze();
            PolicyDecision decision = policyEngine.evaluate(suggestion);
            logs.add(executionGateway.process("RiskAgent", suggestion, decision, inputSummary));
        } catch (Exception e) {
            log.error("RiskAgent orchestration failed", e);
        }

        // 5. Support Agent
        try {
            AgentSuggestion suggestion = supportAgent.analyze();
            PolicyDecision decision = policyEngine.evaluate(suggestion);
            logs.add(executionGateway.process("SupportAgent", suggestion, decision, inputSummary));
        } catch (Exception e) {
            log.error("SupportAgent orchestration failed", e);
        }

        log.info("AgentOrchestrator: Completed synchronous orchestration. Generated {} event logs.", logs.size());
        return logs;
    }

    /**
     * Asynchronously execute the full agent pipeline and write an AgentSuggestionCompleted event
     * to the outbox event store when finished.
     */
    public CompletableFuture<Void> runOrchestrationAsync(String inputSummary) {
        log.info("AgentOrchestrator: Triggered asynchronous orchestration. Input: {}", inputSummary);
        return CompletableFuture.runAsync(() -> {
            try {
                runOrchestrationSync(inputSummary);
                
                // Save the completion event to the outbox for downstream consumers
                OutboxEvent event = OutboxEvent.builder()
                        .aggregateType("AgentOrchestration")
                        .aggregateId(UUID.randomUUID().toString())
                        .eventType("AgentSuggestionCompleted")
                        .payload("{\"inputSummary\":\"" + inputSummary + "\",\"completedAt\":\"" + OffsetDateTime.now() + "\"}")
                        .status("PENDING")
                        .build();
                outboxEventRepo.save(event);
                log.info("AgentOrchestrator: Successfully wrote AgentSuggestionCompleted to outbox.");
            } catch (Exception e) {
                log.error("AgentOrchestrator: Error during async orchestration", e);
            }
        });
    }
}
