import os

base_path = "backend/src/main/java/ch/swissqcommerce/backend/domain/ordermanagement"

dirs = [
    f"{base_path}/core/model",
    f"{base_path}/core/service",
    f"{base_path}/port/in",
    f"{base_path}/port/out",
    f"{base_path}/adapter/in/event",
    f"{base_path}/adapter/out/persistence",
]

for d in dirs:
    os.makedirs(d, exist_ok=True)

files = {
    f"{base_path}/core/model/CustomerOrder.java": """package ch.swissqcommerce.backend.domain.ordermanagement.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrder {
    private String orderId;
    private String customerId;
    private String status; // CREATED, INVENTORY_CONFIRMED, PAYMENT_SUCCESS, DISPATCHED, DELIVERED
    private String sagaState; // PENDING, COMPLETED, COMPENSATING, ABORTED
    private OffsetDateTime createdAt;
}
""",
    f"{base_path}/port/in/OrderManagementUseCase.java": """package ch.swissqcommerce.backend.domain.ordermanagement.port.in;

public interface OrderManagementUseCase {
    void handleOrderCreated(String orderId, String customerId);
    void handleInventoryConfirmed(String orderId);
    void handlePaymentSuccess(String orderId);
    void handleOrderDelivered(String orderId);
    void compensateOrder(String orderId, String reason);
}
""",
    f"{base_path}/port/out/CustomerOrderPort.java": """package ch.swissqcommerce.backend.domain.ordermanagement.port.out;

import ch.swissqcommerce.backend.domain.ordermanagement.core.model.CustomerOrder;
import java.util.Optional;

public interface CustomerOrderPort {
    CustomerOrder save(CustomerOrder order);
    Optional<CustomerOrder> findById(String orderId);
}
""",
    f"{base_path}/core/service/OrderSagaManager.java": """package ch.swissqcommerce.backend.domain.ordermanagement.core.service;

import ch.swissqcommerce.backend.domain.ordermanagement.core.model.CustomerOrder;
import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
import ch.swissqcommerce.backend.domain.ordermanagement.port.out.CustomerOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OrderSagaManager implements OrderManagementUseCase {
    private final CustomerOrderPort orderPort;

    @Override
    public void handleOrderCreated(String orderId, String customerId) {
        CustomerOrder order = CustomerOrder.builder()
                .orderId(orderId)
                .customerId(customerId)
                .status("CREATED")
                .sagaState("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();
        orderPort.save(order);
        // Here we would publish a 'ReserveInventoryCommand' event using the global publisher
    }

    @Override
    public void handleInventoryConfirmed(String orderId) {
        orderPort.findById(orderId).ifPresent(o -> {
            o.setStatus("INVENTORY_CONFIRMED");
            orderPort.save(o);
            // Publish 'ProcessPaymentCommand' event
        });
    }

    @Override
    public void handlePaymentSuccess(String orderId) {
        orderPort.findById(orderId).ifPresent(o -> {
            o.setStatus("PAYMENT_SUCCESS");
            orderPort.save(o);
            // Publish 'DispatchOrderCommand' event
        });
    }

    @Override
    public void handleOrderDelivered(String orderId) {
        orderPort.findById(orderId).ifPresent(o -> {
            o.setStatus("DELIVERED");
            o.setSagaState("COMPLETED");
            orderPort.save(o);
        });
    }

    @Override
    public void compensateOrder(String orderId, String reason) {
        orderPort.findById(orderId).ifPresent(o -> {
            o.setSagaState("ABORTED");
            o.setStatus("CANCELLED");
            orderPort.save(o);
            // Publish 'ReleaseInventoryCommand' and 'RefundPaymentCommand' events
        });
    }
}
""",
    f"{base_path}/adapter/out/persistence/CustomerOrderEntity.java": """package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "saga_customer_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderEntity {
    @Id
    private String orderId;
    private String customerId;
    private String status;
    private String sagaState;
    private OffsetDateTime createdAt;
}
""",
    f"{base_path}/adapter/out/persistence/CustomerOrderRepository.java": """package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrderEntity, String> {
}
""",
    f"{base_path}/adapter/out/persistence/OrderManagementPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.ordermanagement.core.model.CustomerOrder;
import ch.swissqcommerce.backend.domain.ordermanagement.port.out.CustomerOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderManagementPersistenceAdapter implements CustomerOrderPort {
    private final CustomerOrderRepository repository;

    @Override
    public CustomerOrder save(CustomerOrder order) {
        CustomerOrderEntity entity = CustomerOrderEntity.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .sagaState(order.getSagaState())
                .createdAt(order.getCreatedAt())
                .build();
        repository.save(entity);
        return order;
    }

    @Override
    public Optional<CustomerOrder> findById(String orderId) {
        return repository.findById(orderId).map(e -> CustomerOrder.builder()
                .orderId(e.getOrderId())
                .customerId(e.getCustomerId())
                .status(e.getStatus())
                .sagaState(e.getSagaState())
                .createdAt(e.getCreatedAt())
                .build());
    }
}
""",
    f"{base_path}/adapter/in/event/OrderSagaListener.java": """package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.event;

import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import ch.swissqcommerce.backend.domain.event.core.model.BaseDomainEvent;

@Component
@RequiredArgsConstructor
public class OrderSagaListener {

    private final OrderManagementUseCase orderSagaManager;

    @EventListener
    public void handleDomainEvent(BaseDomainEvent event) {
        switch (event.getEventType()) {
            case "OrderPlaced":
                orderSagaManager.handleOrderCreated(event.getAggregateId(), event.getPayload()); // payload holds customerId
                break;
            case "InventoryConfirmed":
                orderSagaManager.handleInventoryConfirmed(event.getAggregateId());
                break;
            case "InventoryFailed":
                orderSagaManager.compensateOrder(event.getAggregateId(), "Inventory Shortage");
                break;
            case "PaymentSuccess":
                orderSagaManager.handlePaymentSuccess(event.getAggregateId());
                break;
            case "PaymentFailed":
                orderSagaManager.compensateOrder(event.getAggregateId(), "Payment Failed");
                break;
            case "OrderDelivered":
                orderSagaManager.handleOrderDelivered(event.getAggregateId());
                break;
        }
    }
}
"""
}

for path, content in files.items():
    with open(path, "w") as f:
        f.write(content)

print("Phase 9 (Order Management Bounded Context) scaffolded successfully!")
