package ch.swissqcommerce.backend.gateway;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.ExecutionRecordRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.PolicyDecisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY layer that touches DB writes for agent-driven actions.
 *
 * <p>Rule: if it's not approved by the Policy Engine, it cannot execute.
 */
@Service
public class ExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(ExecutionGateway.class);

    private final InventoryRepository inventoryRepo;
    private final ObjectMapper objectMapper;
    private final AgentSuggestionEntityRepository agentSuggestionRepo;
    private final PolicyDecisionRepository policyDecisionRepo;
    private final ExecutionRecordRepository executionRecordRepo;
    private final EntityManager entityManager;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public ExecutionGateway(
            InventoryRepository inventoryRepo,
            ObjectMapper objectMapper,
            AgentSuggestionEntityRepository agentSuggestionRepo,
            PolicyDecisionRepository policyDecisionRepo,
            ExecutionRecordRepository executionRecordRepo,
            EntityManager entityManager,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.inventoryRepo = inventoryRepo;
        this.objectMapper = objectMapper;
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.policyDecisionRepo = policyDecisionRepo;
        this.executionRecordRepo = executionRecordRepo;
        this.entityManager = entityManager;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void execute(UUID suggestionId, String executedBy) {
        long startTime = System.nanoTime();
        AgentSuggestionEntity suggestion = null;
        String action = "unknown";
        try {
            suggestion = agentSuggestionRepo.findById(suggestionId).orElse(null);
            if (suggestion == null) {
                throw new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                        "Suggestion not found: " + suggestionId);
            }

            final AgentSuggestionEntity finalSuggestion = suggestion;
            try {
                JsonNode rec = objectMapper.readTree(suggestion.getRecommendation());
                action = rec.has("action") ? rec.get("action").asText() : "unknown";
            } catch (Exception e) {
                // ignore
            }

            // 1. Expiration check
            if (OffsetDateTime.now().isAfter(suggestion.getExpiresAt())) {
                suggestion.setStatus("expired");
                agentSuggestionRepo.save(suggestion);
                saveFailedRecord(suggestion, "EXPIRED", "Suggestion expired at " + suggestion.getExpiresAt(), executedBy);
                if (meterRegistry != null) {
                    meterRegistry.counter("agent_suggestions_total",
                            "domain", suggestion.getDomain(),
                            "decision", "expired",
                            "agent_name", suggestion.getAgent() != null ? suggestion.getAgent().getName() : "UnknownAgent"
                    ).increment();
                }
                throw new IllegalStateException("Suggestion is expired");
            }

            // 2. Status check
            if (!"approved".equalsIgnoreCase(suggestion.getStatus())) {
                throw new IllegalStateException("Suggestion must be in approved status to execute, but was: " 
                        + suggestion.getStatus());
            }

            // 3. State-drift check and execution
            try {
                JsonNode rec = objectMapper.readTree(suggestion.getRecommendation());
                if ("update_price".equalsIgnoreCase(action)) {
                    double expectedOldVal = rec.get("old_value").asDouble();
                    double newVal = rec.get("new_value").asDouble();
                    
                    Inventory item = inventoryRepo.findById(finalSuggestion.getEntityId())
                            .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                                    "Inventory item not found for pricing execution: " + finalSuggestion.getEntityId()));
                                    
                    BigDecimal currentPrice = item.getPrice();
                    BigDecimal expectedOldPrice = BigDecimal.valueOf(expectedOldVal).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newPrice = BigDecimal.valueOf(newVal).setScale(2, RoundingMode.HALF_UP);
                    
                    if (currentPrice.compareTo(expectedOldPrice) != 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "STATE_DRIFT", 
                                "STATE_DRIFT: expected=" + expectedOldPrice + " actual=" + currentPrice, executedBy);
                        throw new OptimisticLockException("Price changed since suggestion");
                    }
                    
                    // Atomic, single-write-path optimistic update to oltp.inventory
                    int updated = entityManager.createNativeQuery(
                            "UPDATE oltp.inventory SET price = :newPrice, updated_at = NOW() WHERE item_id = :sku AND price = :oldPrice")
                            .setParameter("newPrice", newPrice)
                            .setParameter("sku", suggestion.getEntityId())
                            .setParameter("oldPrice", currentPrice)
                            .executeUpdate();

                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "STATE_DRIFT", "Optimistic update affected 0 rows", executedBy);
                        throw new OptimisticLockException("Optimistic lock failure during price update");
                    }
                    
                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(suggestion, objectMapper.writeValueAsString(Map.of("old_price", currentPrice, "new_price", newPrice, "rows_affected", 1)), executedBy);
                    log.info("ExecutionGateway executed pricing suggestion for item: {} new price: {}", suggestion.getEntityId(), newPrice);
                    
                } else if ("restock".equalsIgnoreCase(action)) {
                    int expectedOldVal = rec.get("old_value").asInt();
                    int newVal = rec.get("new_value").asInt();
                    
                    Inventory item = inventoryRepo.findById(finalSuggestion.getEntityId())
                            .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                                    "Inventory item not found for stock execution: " + finalSuggestion.getEntityId()));
                                    
                    int currentStock = item.getStock();
                    if (currentStock != expectedOldVal) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "STATE_DRIFT", 
                                "STATE_DRIFT: expected=" + expectedOldVal + " actual=" + currentStock, executedBy);
                        throw new OptimisticLockException("Stock changed since suggestion");
                    }
                    
                    int updated = inventoryRepo.updateStockOptimistically(suggestion.getEntityId(), currentStock, newVal);
                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "STATE_DRIFT", "Optimistic update affected 0 rows", executedBy);
                        throw new OptimisticLockException("Optimistic lock failure during stock update");
                    }
                    
                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(suggestion, objectMapper.writeValueAsString(Map.of("old_stock", currentStock, "new_stock", newVal, "rows_affected", 1)), executedBy);
                    log.info("ExecutionGateway executed stock suggestion for item: {} new stock: {}", suggestion.getEntityId(), newVal);
                    
                } else if ("hold_order".equalsIgnoreCase(action)) {
                    if (!rec.has("order_id") || !rec.has("version")) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "INVALID_RECOMMENDATION", "hold_order requires order_id and version in recommendation", executedBy);
                        throw new IllegalArgumentException("hold_order requires order_id and version in recommendation");
                    }
                    int orderId = rec.get("order_id").asInt();
                    int expectedVersion = rec.get("version").asInt();

                    int updated = entityManager.createNativeQuery("""
                        UPDATE oltp.orders SET status='held', version=version+1, updated_at=NOW() 
                        WHERE order_id=:orderId AND version=:oldVersion AND status='pending'
                        """)
                        .setParameter("orderId", orderId)
                        .setParameter("oldVersion", expectedVersion)
                        .executeUpdate();

                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(suggestion, "STATE_DRIFT", 
                            "Order hold failed: order_id=" + orderId + " version mismatch or not pending", executedBy);
                        throw new OptimisticLockException("Order state changed since fraud suggestion");
                    }

                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(suggestion, objectMapper.writeValueAsString(
                        Map.of("order_id", orderId, "action", "held", "new_version", expectedVersion + 1)), executedBy);
                    log.info("ExecutionGateway executed fraud hold for order: {}", orderId);

                } else {
                    throw new IllegalArgumentException("Unknown recommendation action for execution: " + action);
                }
            } catch (OptimisticLockException | IllegalStateException | IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("ExecutionGateway error during execute for suggestion: {}", suggestionId, e);
                suggestion.setStatus("failed");
                agentSuggestionRepo.save(suggestion);
                saveFailedRecord(suggestion, "EXECUTION_ERROR", e.getMessage(), executedBy);
                throw new RuntimeException("Execution failed: " + e.getMessage(), e);
            }
        } finally {
            long duration = System.nanoTime() - startTime;
            if (meterRegistry != null && suggestion != null) {
                var timer = meterRegistry.timer("agent_execution_duration_seconds",
                        "domain", suggestion.getDomain(),
                        "action", action);
                if (timer != null) {
                    timer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
            }
        }
    }

    private void saveFailedRecord(AgentSuggestionEntity suggestion, String code, String message, String executedBy) {
        ch.swissqcommerce.backend.model.PolicyDecision decision = getLatestDecision(suggestion);
        ExecutionRecord record = ExecutionRecord.builder()
                .suggestion(suggestion)
                .decision(decision)
                .executed(false)
                .error(code + ": " + message)
                .executedBy(executedBy)
                .build();
        executionRecordRepo.save(record);
    }

    private void saveSuccessRecord(AgentSuggestionEntity suggestion, String resultJson, String executedBy) {
        ch.swissqcommerce.backend.model.PolicyDecision decision = getLatestDecision(suggestion);
        ExecutionRecord record = ExecutionRecord.builder()
                .suggestion(suggestion)
                .decision(decision)
                .executed(true)
                .executionResult(resultJson)
                .executedBy(executedBy)
                .build();
        executionRecordRepo.save(record);
    }

    private ch.swissqcommerce.backend.model.PolicyDecision getLatestDecision(AgentSuggestionEntity suggestion) {
        List<ch.swissqcommerce.backend.model.PolicyDecision> decisions = policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestion.getId());
        return decisions.isEmpty() ? null : decisions.get(0);
    }
}
