package ch.swissqcommerce.backend.gateway;

import ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence.ShipmentEntity;
import ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence.ShipmentRepository;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.ExecutionRecordRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
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
    private final OrderRepository orderRepo;
    private final ShipmentRepository shipmentRepo;
    private final DarkStoreRepository darkStoreRepo;

    public ExecutionGateway(
            InventoryRepository inventoryRepo,
            ObjectMapper objectMapper,
            AgentSuggestionEntityRepository agentSuggestionRepo,
            PolicyDecisionRepository policyDecisionRepo,
            ExecutionRecordRepository executionRecordRepo,
            EntityManager entityManager,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            OrderRepository orderRepo,
            ShipmentRepository shipmentRepo,
            DarkStoreRepository darkStoreRepo) {
        this.inventoryRepo = inventoryRepo;
        this.objectMapper = objectMapper;
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.policyDecisionRepo = policyDecisionRepo;
        this.executionRecordRepo = executionRecordRepo;
        this.entityManager = entityManager;
        this.meterRegistry = meterRegistry;
        this.orderRepo = orderRepo;
        this.shipmentRepo = shipmentRepo;
        this.darkStoreRepo = darkStoreRepo;
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
                saveFailedRecord(
                        suggestion,
                        "EXPIRED",
                        "Suggestion expired at " + suggestion.getExpiresAt(),
                        executedBy);
                if (meterRegistry != null) {
                    meterRegistry
                            .counter(
                                    "agent_suggestions_total",
                                    "domain",
                                    suggestion.getDomain(),
                                    "decision",
                                    "expired",
                                    "agent_name",
                                    suggestion.getAgent() != null
                                            ? suggestion.getAgent().getName()
                                            : "UnknownAgent")
                            .increment();
                }
                throw new IllegalStateException("Suggestion is expired");
            }

            // 2. Status check
            if (!"approved".equalsIgnoreCase(suggestion.getStatus())) {
                throw new IllegalStateException(
                        "Suggestion must be in approved status to execute, but was: "
                                + suggestion.getStatus());
            }

            // 3. State-drift check and execution
            try {
                JsonNode rec = objectMapper.readTree(suggestion.getRecommendation());
                if ("update_price".equalsIgnoreCase(action)) {
                    double expectedOldVal = rec.get("old_value").asDouble();
                    double newVal = rec.get("new_value").asDouble();

                    Inventory item =
                            inventoryRepo
                                    .findById(finalSuggestion.getEntityId())
                                    .orElseThrow(
                                            () ->
                                                    new ch.swissqcommerce.backend.exception
                                                            .ResourceNotFoundException(
                                                            "Inventory item not found for pricing"
                                                                    + " execution: "
                                                                    + finalSuggestion
                                                                            .getEntityId()));

                    BigDecimal currentPrice = item.getPrice();
                    BigDecimal expectedOldPrice =
                            BigDecimal.valueOf(expectedOldVal).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newPrice =
                            BigDecimal.valueOf(newVal).setScale(2, RoundingMode.HALF_UP);

                    if (currentPrice.compareTo(expectedOldPrice) != 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "STATE_DRIFT: expected="
                                        + expectedOldPrice
                                        + " actual="
                                        + currentPrice,
                                executedBy);
                        throw new OptimisticLockException("Price changed since suggestion");
                    }

                    // Atomic, single-write-path optimistic update to oltp.inventory
                    int updated =
                            entityManager
                                    .createNativeQuery(
                                            "UPDATE oltp.inventory SET price = :newPrice,"
                                                    + " updated_at = NOW() WHERE item_id = :sku AND"
                                                    + " price = :oldPrice")
                                    .setParameter("newPrice", newPrice)
                                    .setParameter("sku", suggestion.getEntityId())
                                    .setParameter("oldPrice", currentPrice)
                                    .executeUpdate();

                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "Optimistic update affected 0 rows",
                                executedBy);
                        throw new OptimisticLockException(
                                "Optimistic lock failure during price update");
                    }

                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(
                            suggestion,
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "old_price",
                                            currentPrice,
                                            "new_price",
                                            newPrice,
                                            "rows_affected",
                                            1)),
                            executedBy);
                    log.info(
                            "ExecutionGateway executed pricing suggestion for item: {} new price:"
                                    + " {}",
                            suggestion.getEntityId(),
                            newPrice);

                } else if ("restock".equalsIgnoreCase(action)) {
                    int expectedOldVal = rec.get("old_value").asInt();
                    int newVal = rec.get("new_value").asInt();

                    Inventory item =
                            inventoryRepo
                                    .findById(finalSuggestion.getEntityId())
                                    .orElseThrow(
                                            () ->
                                                    new ch.swissqcommerce.backend.exception
                                                            .ResourceNotFoundException(
                                                            "Inventory item not found for stock"
                                                                    + " execution: "
                                                                    + finalSuggestion
                                                                            .getEntityId()));

                    int currentStock = item.getStock();
                    if (currentStock != expectedOldVal) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "STATE_DRIFT: expected="
                                        + expectedOldVal
                                        + " actual="
                                        + currentStock,
                                executedBy);
                        throw new OptimisticLockException("Stock changed since suggestion");
                    }

                    int updated =
                            inventoryRepo.updateStockOptimistically(
                                    suggestion.getEntityId(), currentStock, newVal);
                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "Optimistic update affected 0 rows",
                                executedBy);
                        throw new OptimisticLockException(
                                "Optimistic lock failure during stock update");
                    }

                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(
                            suggestion,
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "old_stock",
                                            currentStock,
                                            "new_stock",
                                            newVal,
                                            "rows_affected",
                                            1)),
                            executedBy);
                    log.info(
                            "ExecutionGateway executed stock suggestion for item: {} new stock: {}",
                            suggestion.getEntityId(),
                            newVal);

                } else if ("hold_order".equalsIgnoreCase(action)) {
                    if (!rec.has("order_id") || !rec.has("version")) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "INVALID_RECOMMENDATION",
                                "hold_order requires order_id and version in recommendation",
                                executedBy);
                        throw new IllegalArgumentException(
                                "hold_order requires order_id and version in recommendation");
                    }
                    int orderId = rec.get("order_id").asInt();
                    int expectedVersion = rec.get("version").asInt();

                    int updated =
                            entityManager
                                    .createNativeQuery(
                                            """
                        UPDATE oltp.orders SET status='held', version=version+1, updated_at=NOW()
                        WHERE order_id=:orderId AND version=:oldVersion AND status='pending'
                        """)
                                    .setParameter("orderId", orderId)
                                    .setParameter("oldVersion", expectedVersion)
                                    .executeUpdate();

                    if (updated == 0) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "Order hold failed: order_id="
                                        + orderId
                                        + " version mismatch or not pending",
                                executedBy);
                        throw new OptimisticLockException(
                                "Order state changed since fraud suggestion");
                    }

                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);
                    saveSuccessRecord(
                            suggestion,
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "order_id",
                                            orderId,
                                            "action",
                                            "held",
                                            "new_version",
                                            expectedVersion + 1)),
                            executedBy);
                    log.info("ExecutionGateway executed fraud hold for order: {}", orderId);

                } else if ("assign_warehouse".equalsIgnoreCase(action)) {
                    if (!rec.has("order_id") || !rec.has("version")) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "INVALID_RECOMMENDATION",
                                "assign_warehouse requires order_id and version in recommendation",
                                executedBy);
                        throw new IllegalArgumentException(
                                "assign_warehouse requires order_id and version in recommendation");
                    }
                    int orderId = rec.get("order_id").asInt();
                    int expectedVersion = rec.get("version").asInt();
                    boolean splitShipment =
                            rec.has("split_shipment") && rec.get("split_shipment").asBoolean();
                    String primaryWarehouseId =
                            rec.has("primary_warehouse_id")
                                    ? rec.get("primary_warehouse_id").asText()
                                    : null;
                    BigDecimal estimatedShippingCost =
                            rec.has("estimated_shipping_cost")
                                    ? BigDecimal.valueOf(
                                            rec.get("estimated_shipping_cost").asDouble())
                                    : BigDecimal.ZERO;
                    String carrier = rec.has("carrier") ? rec.get("carrier").asText() : "USPS";

                    OrderEntity order =
                            orderRepo
                                    .findById(orderId)
                                    .orElseThrow(
                                            () ->
                                                    new ch.swissqcommerce.backend.exception
                                                            .ResourceNotFoundException(
                                                            "Order not found for logistics"
                                                                    + " execution: "
                                                                    + orderId));

                    if (!order.getVersion().equals(expectedVersion)) {
                        suggestion.setStatus("failed");
                        agentSuggestionRepo.save(suggestion);
                        saveFailedRecord(
                                suggestion,
                                "STATE_DRIFT",
                                "STATE_DRIFT: expected version="
                                        + expectedVersion
                                        + " actual="
                                        + order.getVersion(),
                                executedBy);
                        throw new OptimisticLockException("Order version changed since suggestion");
                    }

                    // 1. Decrement reserved_qty at original items
                    for (OrderItemEntity oitem : order.getOrderItems()) {
                        Inventory originalInv = oitem.getItem();
                        int qty = oitem.getQuantity();
                        int oldReserved =
                                originalInv.getReservedQty() != null
                                        ? originalInv.getReservedQty()
                                        : 0;
                        int newReserved = Math.max(0, oldReserved - qty);
                        originalInv.setReservedQty(newReserved);
                        inventoryRepo.save(originalInv);
                    }

                    // 2. Map target warehouse for each item, increment reserved_qty and update
                    // order_items
                    if (splitShipment) {
                        JsonNode splits = rec.get("warehouse_splits");
                        if (splits == null || !splits.isArray()) {
                            throw new IllegalArgumentException(
                                    "Split shipment requires warehouse_splits array");
                        }
                        for (JsonNode split : splits) {
                            String whId = split.get("warehouse_id").asText();
                            JsonNode skuIdsNode = split.get("sku_ids");
                            for (JsonNode skuIdNode : skuIdsNode) {
                                String itemIdStr = skuIdNode.asText();
                                OrderItemEntity matchedOrderItem =
                                        order.getOrderItems().stream()
                                                .filter(
                                                        oi ->
                                                                oi.getItem()
                                                                        .getItemId()
                                                                        .equals(itemIdStr))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalArgumentException(
                                                                        "Item "
                                                                                + itemIdStr
                                                                                + " not found in"
                                                                                + " order items"));

                                Inventory targetInv =
                                        findInventoryForStore(whId, matchedOrderItem.getItem());
                                int available =
                                        targetInv.getStock()
                                                - (targetInv.getReservedQty() != null
                                                        ? targetInv.getReservedQty()
                                                        : 0);
                                if (available < matchedOrderItem.getQuantity()) {
                                    throw new OptimisticLockException(
                                            "Insufficient stock in target warehouse "
                                                    + whId
                                                    + " for item "
                                                    + targetInv.getName());
                                }
                                targetInv.setReservedQty(
                                        (targetInv.getReservedQty() != null
                                                        ? targetInv.getReservedQty()
                                                        : 0)
                                                + matchedOrderItem.getQuantity());
                                inventoryRepo.save(targetInv);

                                entityManager
                                        .createNativeQuery(
                                                "UPDATE oltp.order_items SET item_id = :newItemId"
                                                        + " WHERE order_id = :orderId AND item_id ="
                                                        + " :oldItemId")
                                        .setParameter("newItemId", targetInv.getItemId())
                                        .setParameter("orderId", orderId)
                                        .setParameter("oldItemId", itemIdStr)
                                        .executeUpdate();
                            }
                        }
                    } else {
                        for (OrderItemEntity oitem : order.getOrderItems()) {
                            Inventory targetInv =
                                    findInventoryForStore(primaryWarehouseId, oitem.getItem());
                            int available =
                                    targetInv.getStock()
                                            - (targetInv.getReservedQty() != null
                                                    ? targetInv.getReservedQty()
                                                    : 0);
                            if (available < oitem.getQuantity()) {
                                throw new OptimisticLockException(
                                        "Insufficient stock in target warehouse "
                                                + primaryWarehouseId
                                                + " for item "
                                                + targetInv.getName());
                            }
                            targetInv.setReservedQty(
                                    (targetInv.getReservedQty() != null
                                                    ? targetInv.getReservedQty()
                                                    : 0)
                                            + oitem.getQuantity());
                            inventoryRepo.save(targetInv);

                            entityManager
                                    .createNativeQuery(
                                            "UPDATE oltp.order_items SET item_id = :newItemId WHERE"
                                                + " order_id = :orderId AND item_id = :oldItemId")
                                    .setParameter("newItemId", targetInv.getItemId())
                                    .setParameter("orderId", orderId)
                                    .setParameter("oldItemId", oitem.getItem().getItemId())
                                    .executeUpdate();
                        }
                    }

                    // 3. Update orders row
                    int updated =
                            entityManager
                                    .createNativeQuery(
                                            """
                        UPDATE oltp.orders
                        SET warehouse_id = :primaryId,
                            estimated_shipping_cost = :estimatedCost,
                            version = version + 1,
                            updated_at = NOW()
                        WHERE order_id = :orderId AND version = :version
                        """)
                                    .setParameter("primaryId", primaryWarehouseId)
                                    .setParameter("estimatedCost", estimatedShippingCost)
                                    .setParameter("orderId", orderId)
                                    .setParameter("version", expectedVersion)
                                    .executeUpdate();

                    if (updated == 0) {
                        throw new OptimisticLockException("Order state changed since suggestion");
                    }

                    // 4. Save shipment records
                    ch.swissqcommerce.backend.model.DarkStore primaryWh =
                            darkStoreRepo
                                    .findById(primaryWarehouseId)
                                    .orElseThrow(
                                            () ->
                                                    new ch.swissqcommerce.backend.exception
                                                            .ResourceNotFoundException(
                                                            "Warehouse not found: "
                                                                    + primaryWarehouseId));

                    if (splitShipment) {
                        JsonNode splits = rec.get("warehouse_splits");
                        for (JsonNode split : splits) {
                            String whId = split.get("warehouse_id").asText();
                            ch.swissqcommerce.backend.model.DarkStore splitWh =
                                    darkStoreRepo
                                            .findById(whId)
                                            .orElseThrow(
                                                    () ->
                                                            new ch.swissqcommerce.backend.exception
                                                                    .ResourceNotFoundException(
                                                                    "Warehouse not found: "
                                                                            + whId));
                            BigDecimal cost =
                                    split.has("estimated_cost")
                                            ? BigDecimal.valueOf(
                                                    split.get("estimated_cost").asDouble())
                                            : estimatedShippingCost;

                            ShipmentEntity shipment =
                                    ShipmentEntity.builder()
                                            .order(order)
                                            .warehouse(splitWh)
                                            .carrier(carrier)
                                            .estimatedShippingCost(cost)
                                            .status("pending")
                                            .build();
                            shipmentRepo.save(shipment);
                        }
                    } else {
                        ShipmentEntity shipment =
                                ShipmentEntity.builder()
                                        .order(order)
                                        .warehouse(primaryWh)
                                        .carrier(carrier)
                                        .estimatedShippingCost(estimatedShippingCost)
                                        .status("pending")
                                        .build();
                        shipmentRepo.save(shipment);
                    }

                    suggestion.setStatus("executed");
                    agentSuggestionRepo.save(suggestion);

                    saveSuccessRecord(
                            suggestion,
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "order_id", orderId,
                                            "action", "assign_warehouse",
                                            "primary_warehouse_id", primaryWarehouseId,
                                            "split_shipment", splitShipment,
                                            "new_version", expectedVersion + 1)),
                            executedBy);
                    log.info("ExecutionGateway executed warehouse routing for order: {}", orderId);

                } else {
                    throw new IllegalArgumentException(
                            "Unknown recommendation action for execution: " + action);
                }
            } catch (OptimisticLockException | IllegalStateException | IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error(
                        "ExecutionGateway error during execute for suggestion: {}",
                        suggestionId,
                        e);
                suggestion.setStatus("failed");
                agentSuggestionRepo.save(suggestion);
                saveFailedRecord(suggestion, "EXECUTION_ERROR", e.getMessage(), executedBy);
                throw new RuntimeException("Execution failed: " + e.getMessage(), e);
            }
        } finally {
            long duration = System.nanoTime() - startTime;
            if (meterRegistry != null && suggestion != null) {
                var timer =
                        meterRegistry.timer(
                                "agent_execution_duration_seconds",
                                "domain",
                                suggestion.getDomain(),
                                "action",
                                action);
                if (timer != null) {
                    timer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
            }
        }
    }

    private Inventory findInventoryForStore(String storeId, Inventory originalItem) {
        List<Inventory> targetInventory = inventoryRepo.findByStoreStoreId(storeId);
        for (Inventory inv : targetInventory) {
            if (inv.getName().equalsIgnoreCase(originalItem.getName())) {
                return inv;
            }
        }
        for (Inventory inv : targetInventory) {
            if (inv.getItemId().equals(originalItem.getItemId())) {
                return inv;
            }
        }
        throw new OptimisticLockException(
                "Inventory item "
                        + originalItem.getName()
                        + " not found in target store "
                        + storeId);
    }

    private void saveFailedRecord(
            AgentSuggestionEntity suggestion, String code, String message, String executedBy) {
        ch.swissqcommerce.backend.model.PolicyDecision decision = getLatestDecision(suggestion);
        ExecutionRecord record =
                ExecutionRecord.builder()
                        .suggestion(suggestion)
                        .decision(decision)
                        .executed(false)
                        .error(code + ": " + message)
                        .executedBy(executedBy)
                        .build();
        executionRecordRepo.save(record);
    }

    private void saveSuccessRecord(
            AgentSuggestionEntity suggestion, String resultJson, String executedBy) {
        ch.swissqcommerce.backend.model.PolicyDecision decision = getLatestDecision(suggestion);
        ExecutionRecord record =
                ExecutionRecord.builder()
                        .suggestion(suggestion)
                        .decision(decision)
                        .executed(true)
                        .executionResult(resultJson)
                        .executedBy(executedBy)
                        .build();
        executionRecordRepo.save(record);
    }

    private ch.swissqcommerce.backend.model.PolicyDecision getLatestDecision(
            AgentSuggestionEntity suggestion) {
        List<ch.swissqcommerce.backend.model.PolicyDecision> decisions =
                policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestion.getId());
        return decisions.isEmpty() ? null : decisions.get(0);
    }
}
