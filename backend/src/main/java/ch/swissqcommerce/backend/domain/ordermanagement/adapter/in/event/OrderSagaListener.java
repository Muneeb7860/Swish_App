package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.event;
import ch.swissqcommerce.backend.model.Inventory;


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
