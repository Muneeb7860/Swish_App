package ch.swissqcommerce.backend.gateway;

import ch.swissqcommerce.backend.agent.AgentSuggestion;
import ch.swissqcommerce.backend.model.AgentEventLog;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.policy.PolicyDecision;
import ch.swissqcommerce.backend.repository.AgentEventLogRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY layer that touches DB writes for agent-driven actions.
 *
 * <p>Rule: if it's not approved by the Policy Engine, it cannot execute.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validates that the policy decision is "approved"</li>
 *   <li>For "needs_human" → auto-creates a HITL queue task</li>
 *   <li>Logs every suggestion + decision to the agent_event_log</li>
 *   <li>Dispatches approved actions to the appropriate domain service</li>
 * </ul>
 */
@Service
public class ExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(ExecutionGateway.class);

    private final AgentEventLogRepository eventLogRepo;
    private final HitlQueueRepository hitlQueueRepo;
    private final InventoryRepository inventoryRepo;
    private final ObjectMapper objectMapper;

    public ExecutionGateway(
            AgentEventLogRepository eventLogRepo,
            HitlQueueRepository hitlQueueRepo,
            InventoryRepository inventoryRepo,
            ObjectMapper objectMapper) {
        this.eventLogRepo = eventLogRepo;
        this.hitlQueueRepo = hitlQueueRepo;
        this.inventoryRepo = inventoryRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Process an agent suggestion through the gateway.
     * Always logs to the event log. Only executes if approved.
     * Auto-creates HITL task if needs_human.
     */
    @Transactional
    public AgentEventLog process(
            String agentName, AgentSuggestion suggestion, PolicyDecision decision, String inputSummary) {

        String outputJson;
        try {
            outputJson = objectMapper.writeValueAsString(suggestion);
        } catch (JsonProcessingException e) {
            outputJson = "{\"error\": \"serialization_failed\", \"action\": \""
                    + suggestion.action() + "\"}";
        }

        boolean executed = false;

        // Gate: only approved actions get executed
        if (decision.isApproved()) {
            executeApprovedAction(suggestion);
            executed = true;
            log.info("ExecutionGateway EXECUTED: agent={}, domain={}, action={}",
                    agentName, suggestion.domain(), suggestion.action());
        } else if ("needs_human".equals(decision.status())) {
            createHitlTask(agentName, suggestion, decision);
            log.info("ExecutionGateway → HITL: agent={}, domain={}, reason={}",
                    agentName, suggestion.domain(), decision.reason());
        } else {
            log.info("ExecutionGateway BLOCKED: agent={}, status={}, reason={}",
                    agentName, decision.status(), decision.reason());
        }

        // Always log — every suggestion is audited
        AgentEventLog entry = AgentEventLog.builder()
                .eventType("agent_suggestion")
                .agent(agentName)
                .domain(suggestion.domain())
                .inputSummary(inputSummary)
                .outputJson(outputJson)
                .policyStatus(decision.status())
                .policyReason(decision.reason())
                .executed(executed)
                .build();

        return eventLogRepo.save(entry);
    }

    /**
     * Dispatch approved actions to the appropriate domain service.
     * Closed loop: pricing and inventory write directly to the database.
     */
    public void executeApprovedAction(AgentSuggestion suggestion) {
        switch (suggestion.domain()) {
            case "inventory" -> executeInventoryAction(suggestion);
            case "pricing" -> executePricingAction(suggestion);
            case "routing" -> log.info("Routing action approved for execution: {}", suggestion.action());
            case "risk" -> log.info("Risk action approved for execution: {}", suggestion.action());
            case "support" -> log.info("Support draft generated: {}", suggestion.action());
            default -> log.warn("Unknown domain for execution: {}", suggestion.domain());
        }
    }

    private void executePricingAction(AgentSuggestion suggestion) {
        String action = suggestion.action();
        double changePct = ch.swissqcommerce.backend.policy.PolicyEngine.extractPercentageChange(action);
        if (changePct == 0.0) {
            log.warn("ExecutionGateway: Price change percentage not found in action '{}'", action);
            return;
        }

        List<Inventory> items = inventoryRepo.findAll();
        boolean updated = false;
        for (Inventory item : items) {
            if (action.toLowerCase().contains(item.getName().toLowerCase())) {
                BigDecimal oldPrice = item.getPrice();
                BigDecimal multiplier = BigDecimal.valueOf(1.0 + (changePct / 100.0));
                BigDecimal newPrice = oldPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                item.setPrice(newPrice);
                inventoryRepo.save(item);
                log.info("ExecutionGateway: Price for item '{}' (ID: {}) updated from {} to {}",
                        item.getName(), item.getItemId(), oldPrice, newPrice);
                updated = true;
                break;
            }
        }
        if (!updated) {
            log.warn("ExecutionGateway: No inventory item name matched in action '{}'", action);
        }
    }

    private void executeInventoryAction(AgentSuggestion suggestion) {
        String action = suggestion.action();
        List<Inventory> items = inventoryRepo.findAll();
        boolean updated = false;
        for (Inventory item : items) {
            if (action.toLowerCase().contains(item.getName().toLowerCase())) {
                int addStock = 50; // default restock quantity
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(action);
                if (m.find()) {
                    try {
                        addStock = Integer.parseInt(m.group());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                int oldStock = item.getStock();
                int newStock = oldStock + addStock;
                item.setStock(newStock);
                inventoryRepo.save(item);
                log.info("ExecutionGateway: Stock for item '{}' (ID: {}) restocked from {} by adding {} to {}",
                        item.getName(), item.getItemId(), oldStock, addStock, newStock);
                updated = true;
                break;
            }
        }
        if (!updated) {
            log.warn("ExecutionGateway: No inventory item name matched for restocking in action '{}'", action);
        }
    }

    /**
     * Auto-create a HITL queue task. Humans should never have to read logs
     * to find things that need their attention.
     */
    private void createHitlTask(String agentName, AgentSuggestion suggestion, PolicyDecision decision) {
        String ticketId = "AGENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HitlQueue ticket = HitlQueue.builder()
                .ticketId(ticketId)
                .type("agent_" + suggestion.domain())
                .description(String.format(
                        "[%s] %s — Confidence: %.2f, Impact: %s. Policy: %s",
                        agentName,
                        suggestion.action(),
                        suggestion.confidence(),
                        suggestion.impact(),
                        decision.reason()))
                .amount(BigDecimal.ZERO)
                .status("pending")
                .build();

        hitlQueueRepo.save(ticket);
        log.info("HITL task created: ticketId={}, agent={}, domain={}",
                ticketId, agentName, suggestion.domain());
    }
}
