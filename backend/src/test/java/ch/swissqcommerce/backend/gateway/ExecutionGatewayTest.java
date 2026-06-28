package ch.swissqcommerce.backend.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence.ShipmentRepository;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.model.PolicyDecision;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.ExecutionRecordRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
import ch.swissqcommerce.backend.repository.PolicyDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExecutionGatewayTest {

    @Mock private InventoryRepository inventoryRepo;

    @Mock private AgentSuggestionEntityRepository agentSuggestionRepo;

    @Mock private PolicyDecisionRepository policyDecisionRepo;

    @Mock private ExecutionRecordRepository executionRecordRepo;

    @Mock private EntityManager entityManager;

    @Mock private OrderRepository orderRepo;

    @Mock private ShipmentRepository shipmentRepo;

    @Mock private DarkStoreRepository darkStoreRepo;

    private ExecutionGateway executionGateway;

    @BeforeEach
    public void setUp() {
        executionGateway =
                new ExecutionGateway(
                        inventoryRepo,
                        new ObjectMapper(),
                        agentSuggestionRepo,
                        policyDecisionRepo,
                        executionRecordRepo,
                        entityManager,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                        orderRepo,
                        shipmentRepo,
                        darkStoreRepo);
    }

    @Test
    public void testExecute_Pricing_Success() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .entityId("SKU-123")
                        .recommendation(
                                "{\"action\":\"update_price\",\"old_value\":10.00,\"new_value\":10.50}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        Inventory item =
                Inventory.builder().itemId("SKU-123").price(new BigDecimal("10.00")).build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        when(inventoryRepo.findById("SKU-123")).thenReturn(Optional.of(item));

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(1);

        PolicyDecision decision = PolicyDecision.builder().id(50L).build();
        when(policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestionId))
                .thenReturn(List.of(decision));

        executionGateway.execute(suggestionId, "manager-alice");

        assertEquals("executed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_Pricing_StateDrift() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .entityId("SKU-123")
                        .recommendation(
                                "{\"action\":\"update_price\",\"old_value\":10.00,\"new_value\":10.50}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        // Database value has drifted to 12.00!
        Inventory item =
                Inventory.builder().itemId("SKU-123").price(new BigDecimal("12.00")).build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        when(inventoryRepo.findById("SKU-123")).thenReturn(Optional.of(item));

        assertThrows(
                OptimisticLockException.class,
                () -> {
                    executionGateway.execute(suggestionId, "manager-alice");
                });

        assertEquals("failed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_Pricing_Expired() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .entityId("SKU-123")
                        .recommendation(
                                "{\"action\":\"update_price\",\"old_value\":10.00,\"new_value\":10.50}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().minusMinutes(5)) // expired 5 minutes ago!
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        assertThrows(
                IllegalStateException.class,
                () -> {
                    executionGateway.execute(suggestionId, "manager-alice");
                });

        assertEquals("expired", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_Stock_Success() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("inventory")
                        .entityId("SKU-123")
                        .recommendation(
                                "{\"action\":\"restock\",\"old_value\":10,\"new_value\":60}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        Inventory item = Inventory.builder().itemId("SKU-123").stock(10).build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        when(inventoryRepo.findById("SKU-123")).thenReturn(Optional.of(item));
        when(inventoryRepo.updateStockOptimistically("SKU-123", 10, 60)).thenReturn(1);

        PolicyDecision decision = PolicyDecision.builder().id(51L).build();
        when(policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestionId))
                .thenReturn(List.of(decision));

        executionGateway.execute(suggestionId, "manager-alice");

        assertEquals("executed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_Stock_StateDrift() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("inventory")
                        .entityId("SKU-123")
                        .recommendation(
                                "{\"action\":\"restock\",\"old_value\":10,\"new_value\":60}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        // Database value has drifted to 15!
        Inventory item = Inventory.builder().itemId("SKU-123").stock(15).build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        when(inventoryRepo.findById("SKU-123")).thenReturn(Optional.of(item));

        assertThrows(
                OptimisticLockException.class,
                () -> {
                    executionGateway.execute(suggestionId, "manager-alice");
                });

        assertEquals("failed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_NotApproved() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .entityId("SKU-123")
                        .status("pending") // not approved!
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        assertThrows(
                IllegalStateException.class,
                () -> {
                    executionGateway.execute(suggestionId, "manager-alice");
                });

        verifyNoInteractions(inventoryRepo);
        verifyNoInteractions(executionRecordRepo);
    }

    @Test
    public void testExecute_HoldOrder_Success() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("risk")
                        .entityId("order_id=456")
                        .recommendation(
                                "{\"action\":\"hold_order\",\"order_id\":456,\"version\":0}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(1);

        PolicyDecision decision = PolicyDecision.builder().id(52L).build();
        when(policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestionId))
                .thenReturn(List.of(decision));

        executionGateway.execute(suggestionId, "analyst-bob");

        assertEquals("executed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_HoldOrder_StateDrift() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("risk")
                        .entityId("order_id=456")
                        .recommendation(
                                "{\"action\":\"hold_order\",\"order_id\":456,\"version\":0}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate())
                .thenReturn(0); // 0 rows updated (either version mismatch or not pending)

        assertThrows(
                OptimisticLockException.class,
                () -> {
                    executionGateway.execute(suggestionId, "analyst-bob");
                });

        assertEquals("failed", suggestion.getStatus());
        verify(agentSuggestionRepo).save(suggestion);
        verify(executionRecordRepo).save(any(ExecutionRecord.class));
    }

    @Test
    public void testExecute_HoldOrder_InvalidRecommendation() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("risk")
                        .entityId("order_id=456")
                        .recommendation("{\"action\":\"hold_order\"}") // missing order_id/version
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    executionGateway.execute(suggestionId, "analyst-bob");
                });

        assertEquals("failed", suggestion.getStatus());
    }

    @Test
    public void testExecute_AssignWarehouse_SingleShipment_Success() {
        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("routing")
                        .entityId("order_id=101")
                        .recommendation(
                                "{\"action\":\"assign_warehouse\",\"order_id\":101,\"version\":0,\"split_shipment\":false,\"primary_warehouse_id\":\"WH-NY-01\",\"estimated_shipping_cost\":8.50,\"carrier\":\"USPS\"}")
                        .status("approved")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        ch.swissqcommerce.backend.model.DarkStore originalStore =
                ch.swissqcommerce.backend.model.DarkStore.builder().storeId("store-test-1").build();
        Inventory originalInv =
                Inventory.builder()
                        .itemId("item-1")
                        .name("Item 1")
                        .store(originalStore)
                        .stock(10)
                        .reservedQty(2)
                        .build();

        ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity
                orderItem =
                        ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                                .OrderItemEntity.builder()
                                .item(originalInv)
                                .quantity(2)
                                .build();

        OrderEntity order =
                OrderEntity.builder()
                        .orderId(101)
                        .version(0)
                        .store(originalStore)
                        .orderItems(List.of(orderItem))
                        .build();

        Inventory targetInv =
                Inventory.builder()
                        .itemId("item-1-NY")
                        .name("Item 1")
                        .stock(10)
                        .reservedQty(0)
                        .build();

        ch.swissqcommerce.backend.model.DarkStore targetStore =
                ch.swissqcommerce.backend.model.DarkStore.builder().storeId("WH-NY-01").build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        when(orderRepo.findById(101)).thenReturn(Optional.of(order));
        when(inventoryRepo.findByStoreStoreId("WH-NY-01")).thenReturn(List.of(targetInv));
        when(darkStoreRepo.findById("WH-NY-01")).thenReturn(Optional.of(targetStore));

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(1);

        PolicyDecision decision = PolicyDecision.builder().id(60L).build();
        when(policyDecisionRepo.findBySuggestionIdOrderByIdDesc(suggestionId))
                .thenReturn(List.of(decision));

        executionGateway.execute(suggestionId, "manager-alice");

        assertEquals("executed", suggestion.getStatus());
        assertEquals(0, originalInv.getReservedQty());
        assertEquals(2, targetInv.getReservedQty());
        verify(inventoryRepo).save(originalInv);
        verify(inventoryRepo).save(targetInv);
        verify(shipmentRepo).save(any());
    }
}
