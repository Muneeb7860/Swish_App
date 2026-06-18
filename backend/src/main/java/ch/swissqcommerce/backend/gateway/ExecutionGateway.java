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

    public ExecutionGateway(
            InventoryRepository inventoryRepo,
            ObjectMapper objectMapper,
            AgentSuggestionEntityRepository agentSuggestionRepo,
            PolicyDecisionRepository policyDecisionRepo,
            ExecutionRecordRepository executionRecordRepo,
            EntityManager entityManager) {
        this.inventoryRepo = inventoryRepo;
        this.objectMapper = objectMapper;
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.policyDecisionRepo = policyDecisionRepo;
        this.executionRecordRepo = executionRecordRepo;
        this.entityManager = entityManager;
    }

    @Transactional
    public void execute(UUID suggestionId, String executedBy) {
        AgentSuggestionEntity suggestion = agentSuggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                        "Suggestion not found: " + suggestionId));

        // 1. Expiration check
        if (OffsetDateTime.now().isAfter(suggestion.getExpiresAt())) {
            suggestion.setStatus("expired");
            agentSuggestionRepo.save(suggestion);
            saveFailedRecord(suggestion, "EXPIRED", "Suggestion expired at " + suggestion.getExpiresAt(), executedBy);
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
            String action = rec.has("action") ? rec.get("action").asText() : "";
            
            if ("update_price".equalsIgnoreCase(action)) {
                double expectedOldVal = rec.get("old_value").asDouble();
                double newVal = rec.get("new_value").asDouble();
                
                Inventory item = inventoryRepo.findById(suggestion.getEntityId())
                        .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                                "Inventory item not found for pricing execution: " + suggestion.getEntityId()));
                                
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
                
                Inventory item = inventoryRepo.findById(suggestion.getEntityId())
                        .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                                "Inventory item not found for stock execution: " + suggestion.getEntityId()));
                                
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
