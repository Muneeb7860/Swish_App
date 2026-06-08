package ch.swissqcommerce.backend.domain.ordermanagement.core.service;

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
