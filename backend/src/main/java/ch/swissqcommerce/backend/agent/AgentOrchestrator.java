package ch.swissqcommerce.backend.agent;

import ch.swissqcommerce.backend.gateway.ExecutionGateway;
import ch.swissqcommerce.backend.model.AgentRegistry;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.policy.PolicyDecision;
import ch.swissqcommerce.backend.policy.PolicyEngine;
import ch.swissqcommerce.backend.repository.AgentRegistryRepository;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import ch.swissqcommerce.backend.repository.PolicyDecisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final AgentRegistryRepository agentRegistryRepo;
    private final AgentSuggestionEntityRepository agentSuggestionRepo;
    private final PolicyDecisionRepository policyDecisionRepo;
    private final HitlQueueRepository hitlQueueRepo;
    private final InventoryRepository inventoryRepo;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public AgentOrchestrator(
            OpsAgent opsAgent,
            RoutingAgent routingAgent,
            PricingAgent pricingAgent,
            RiskAgent riskAgent,
            SupportAgent supportAgent,
            PolicyEngine policyEngine,
            ExecutionGateway executionGateway,
            OutboxEventRepository outboxEventRepo,
            AgentRegistryRepository agentRegistryRepo,
            AgentSuggestionEntityRepository agentSuggestionRepo,
            PolicyDecisionRepository policyDecisionRepo,
            HitlQueueRepository hitlQueueRepo,
            InventoryRepository inventoryRepo,
            ObjectMapper objectMapper,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.opsAgent = opsAgent;
        this.routingAgent = routingAgent;
        this.pricingAgent = pricingAgent;
        this.riskAgent = riskAgent;
        this.supportAgent = supportAgent;
        this.policyEngine = policyEngine;
        this.executionGateway = executionGateway;
        this.outboxEventRepo = outboxEventRepo;
        this.agentRegistryRepo = agentRegistryRepo;
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.policyDecisionRepo = policyDecisionRepo;
        this.hitlQueueRepo = hitlQueueRepo;
        this.inventoryRepo = inventoryRepo;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** Synchronously execute the full agent pipeline for debugging or direct API requests. */
    public List<AgentSuggestionEntity> runOrchestrationSync(String inputSummary) {
        log.info("AgentOrchestrator: Starting synchronous orchestration. Input: {}", inputSummary);
        List<AgentSuggestionEntity> logs = new ArrayList<>();

        // Generate trace ID for the request block
        ch.swissqcommerce.backend.config.TraceContext.getTraceId();

        try {
            // 1. Ops Agent (Inventory)
            try {
                AgentSuggestion suggestion = opsAgent.analyze();
                AgentSuggestionEntity entity =
                        processAgentPipeline("OpsAgent", suggestion, inputSummary);
                if (entity != null) logs.add(entity);
            } catch (Exception e) {
                log.error("OpsAgent orchestration failed", e);
            }

            // 2. Routing Agent
            try {
                AgentSuggestion suggestion = routingAgent.analyze();
                AgentSuggestionEntity entity =
                        processAgentPipeline("RoutingAgent", suggestion, inputSummary);
                if (entity != null) logs.add(entity);
            } catch (Exception e) {
                log.error("RoutingAgent orchestration failed", e);
            }

            // 3. Pricing Agent
            try {
                AgentSuggestion suggestion = pricingAgent.analyze();
                AgentSuggestionEntity entity =
                        processAgentPipeline("PricingAgent", suggestion, inputSummary);
                if (entity != null) logs.add(entity);
            } catch (Exception e) {
                log.error("PricingAgent orchestration failed", e);
            }

            // 4. Risk Agent
            try {
                AgentSuggestion suggestion = riskAgent.analyze();
                AgentSuggestionEntity entity =
                        processAgentPipeline("RiskAgent", suggestion, inputSummary);
                if (entity != null) logs.add(entity);
            } catch (Exception e) {
                log.error("RiskAgent orchestration failed", e);
            }

            // 5. Support Agent
            try {
                AgentSuggestion suggestion = supportAgent.analyze();
                AgentSuggestionEntity entity =
                        processAgentPipeline("SupportAgent", suggestion, inputSummary);
                if (entity != null) logs.add(entity);
            } catch (Exception e) {
                log.error("SupportAgent orchestration failed", e);
            }

            log.info(
                    "AgentOrchestrator: Completed synchronous orchestration. Generated {}"
                            + " suggestion entities.",
                    logs.size());
        } finally {
            ch.swissqcommerce.backend.config.TraceContext.clear();
        }
        return logs;
    }

    /**
     * Asynchronously execute the full agent pipeline and write an AgentSuggestionCompleted event to
     * the outbox event store when finished.
     */
    public CompletableFuture<Void> runOrchestrationAsync(String inputSummary) {
        log.info(
                "AgentOrchestrator: Triggered asynchronous orchestration. Input: {}", inputSummary);
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        runOrchestrationSync(inputSummary);

                        OutboxEvent event =
                                OutboxEvent.builder()
                                        .aggregateType("AgentOrchestration")
                                        .aggregateId(UUID.randomUUID().toString())
                                        .eventType("AgentSuggestionCompleted")
                                        .payload(
                                                "{\"inputSummary\":\""
                                                        + inputSummary
                                                        + "\",\"completedAt\":\""
                                                        + OffsetDateTime.now()
                                                        + "\"}")
                                        .status("PENDING")
                                        .build();
                        outboxEventRepo.save(event);
                        log.info(
                                "AgentOrchestrator: Successfully wrote AgentSuggestionCompleted to"
                                        + " outbox.");
                    } catch (Exception e) {
                        log.error("AgentOrchestrator: Error during async orchestration", e);
                    }
                });
    }

    private AgentSuggestionEntity processAgentPipeline(
            String agentName, AgentSuggestion suggestion, String inputSummary) {

        AgentRegistry registry = agentRegistryRepo.findById(agentName).orElse(null);
        if (registry != null && "inactive".equalsIgnoreCase(registry.getStatus())) {
            log.info("AgentOrchestrator: Skipping inactive agent: {}", agentName);
            return null;
        }

        String recommendationJson;
        try {
            if (suggestion.action() != null && suggestion.action().trim().startsWith("{")) {
                recommendationJson = suggestion.action().trim();
            } else if ("pricing".equalsIgnoreCase(suggestion.domain())) {
                recommendationJson = parsePricingRecommendationJson(suggestion);
            } else if ("inventory".equalsIgnoreCase(suggestion.domain())) {
                recommendationJson = parseInventoryRecommendationJson(suggestion);
            } else if ("risk".equalsIgnoreCase(suggestion.domain())
                    && suggestion.action().toLowerCase().contains("hold_order")) {
                recommendationJson = parseRiskRecommendationJson(suggestion);
            } else {
                recommendationJson =
                        objectMapper.writeValueAsString(Map.of("action", suggestion.action()));
            }
        } catch (Exception e) {
            recommendationJson = "{\"action\":\"" + suggestion.action() + "\"}";
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(3);

        AgentSuggestionEntity suggestionEntity =
                AgentSuggestionEntity.builder()
                        .traceId(ch.swissqcommerce.backend.config.TraceContext.getTraceId())
                        .agent(registry)
                        .domain(suggestion.domain())
                        .entityId(extractEntityId(suggestion))
                        .recommendation(recommendationJson)
                        .confidence(BigDecimal.valueOf(suggestion.confidence()))
                        .reason(suggestion.reason())
                        .impact(suggestion.impact())
                        .status("pending")
                        .expiresAt(expiresAt)
                        .build();

        suggestionEntity = agentSuggestionRepo.save(suggestionEntity);

        PolicyDecision decision = policyEngine.evaluate(suggestionEntity);

        ch.swissqcommerce.backend.model.PolicyDecision policyDecisionEntity =
                ch.swissqcommerce.backend.model.PolicyDecision.builder()
                        .suggestion(suggestionEntity)
                        .decision(decision.status())
                        .policyVersion("v1")
                        .reason(decision.reason())
                        .decidedBy("policy_engine_v1")
                        .build();
        policyDecisionRepo.save(policyDecisionEntity);

        if (meterRegistry != null) {
            var counter =
                    meterRegistry.counter(
                            "agent_suggestions_total",
                            "domain",
                            suggestionEntity.getDomain(),
                            "decision",
                            decision.status(),
                            "agent_name",
                            agentName);
            if (counter != null) {
                counter.increment();
            }
        }

        if ("approved".equals(decision.status())) {
            suggestionEntity.setStatus("approved");
            agentSuggestionRepo.save(suggestionEntity);
            try {
                executionGateway.execute(suggestionEntity.getId(), "AgentOrchestrator");
            } catch (Exception e) {
                log.error(
                        "ExecutionGateway failed for suggestion: {}", suggestionEntity.getId(), e);
            }
        } else if ("needs_human".equals(decision.status())) {
            suggestionEntity.setStatus("pending");
            agentSuggestionRepo.save(suggestionEntity);
            createHitlTask(agentName, suggestionEntity, decision);
        } else {
            suggestionEntity.setStatus("rejected");
            agentSuggestionRepo.save(suggestionEntity);
        }

        return suggestionEntity;
    }

    private String parsePricingRecommendationJson(AgentSuggestion s) {
        String action = s.action();
        double percent = PolicyEngine.extractPercentageChange(action);

        List<Inventory> items = inventoryRepo.findAll();
        for (Inventory item : items) {
            if (action.toLowerCase().contains(item.getName().toLowerCase())) {
                BigDecimal oldPrice = item.getPrice();
                BigDecimal multiplier = BigDecimal.valueOf(1.0 + (percent / 100.0));
                BigDecimal newPrice =
                        oldPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

                return String.format(
                        "{\"action\":\"update_price\",\"field\":\"base_price\",\"old_value\":%.2f,\"new_value\":%.2f}",
                        oldPrice.doubleValue(), newPrice.doubleValue());
            }
        }
        return "{\"action\":\"update_price\",\"field\":\"base_price\",\"old_value\":10.00,\"new_value\":10.50}";
    }

    private String parseInventoryRecommendationJson(AgentSuggestion s) {
        String action = s.action();
        List<Inventory> items = inventoryRepo.findAll();
        for (Inventory item : items) {
            if (action.toLowerCase().contains(item.getName().toLowerCase())) {
                int oldStock = item.getStock();
                int addStock = 50;
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(action);
                if (m.find()) {
                    try {
                        addStock = Integer.parseInt(m.group());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                int newStock = oldStock + addStock;
                return String.format(
                        "{\"action\":\"restock\",\"field\":\"stock\",\"old_value\":%d,\"new_value\":%d}",
                        oldStock, newStock);
            }
        }
        return "{\"action\":\"restock\",\"field\":\"stock\",\"old_value\":10,\"new_value\":60}";
    }

    private String parseRiskRecommendationJson(AgentSuggestion s) {
        String action = s.action();
        int orderId = 0;
        int version = 0;

        java.util.regex.Matcher mOrderId =
                java.util.regex.Pattern.compile("order_id=(\\d+)").matcher(action);
        if (mOrderId.find()) {
            orderId = Integer.parseInt(mOrderId.group(1));
        } else {
            java.util.regex.Matcher mNum =
                    java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(action);
            if (mNum.find()) {
                orderId = Integer.parseInt(mNum.group());
            }
        }

        java.util.regex.Matcher mVersion =
                java.util.regex.Pattern.compile("version=(\\d+)").matcher(action);
        if (mVersion.find()) {
            version = Integer.parseInt(mVersion.group(1));
        }

        return String.format(
                "{\"action\":\"hold_order\",\"order_id\":%d,\"version\":%d}", orderId, version);
    }

    private String extractEntityId(AgentSuggestion s) {
        if ("risk".equalsIgnoreCase(s.domain())) {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("order_id=(\\d+)").matcher(s.action());
            if (m.find()) {
                return "order_id=" + m.group(1);
            }
            java.util.regex.Matcher mNum =
                    java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(s.action());
            if (mNum.find()) {
                return "order_id=" + mNum.group();
            }
            return "order_id=0";
        }
        if ("routing".equalsIgnoreCase(s.domain())) {
            try {
                JsonNode node = objectMapper.readTree(s.action());
                if (node.has("order_id")) {
                    return "order_id=" + node.get("order_id").asInt();
                }
            } catch (Exception e) {
                // ignore, try regex
            }
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("order_id=(\\d+)").matcher(s.action());
            if (m.find()) {
                return "order_id=" + m.group(1);
            }
            java.util.regex.Matcher mNum =
                    java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(s.action());
            if (mNum.find()) {
                return "order_id=" + mNum.group();
            }
            return "order_id=0";
        }
        String action = s.action();
        List<Inventory> items = inventoryRepo.findAll();
        for (Inventory item : items) {
            if (action.toLowerCase().contains(item.getName().toLowerCase())) {
                return item.getItemId();
            }
        }
        return "SKU-12345";
    }

    private void createHitlTask(
            String agentName, AgentSuggestionEntity suggestion, PolicyDecision decision) {
        String ticketId = "AGENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HitlQueue ticket =
                HitlQueue.builder()
                        .ticketId(ticketId)
                        .type("agent_" + suggestion.getDomain())
                        .description(
                                String.format(
                                        "[%s] %s — Confidence: %.2f, Impact: %s. Policy: %s",
                                        agentName,
                                        suggestion.getReason(),
                                        suggestion.getConfidence().doubleValue(),
                                        suggestion.getImpact(),
                                        decision.reason()))
                        .amount(BigDecimal.ZERO)
                        .status("pending")
                        .build();

        hitlQueueRepo.save(ticket);
        log.info(
                "HITL task created: ticketId={}, agent={}, domain={}",
                ticketId,
                agentName,
                suggestion.getDomain());
    }
}
