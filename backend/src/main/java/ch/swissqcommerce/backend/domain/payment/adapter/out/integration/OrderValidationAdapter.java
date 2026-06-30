package ch.swissqcommerce.backend.domain.payment.adapter.out.integration;

import ch.swissqcommerce.backend.domain.payment.port.out.OrderValidationPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import org.springframework.stereotype.Component;

@Component
public class OrderValidationAdapter implements OrderValidationPort {

    private final OrderPort orderPort;

    public OrderValidationAdapter(OrderPort orderPort) {
        this.orderPort = orderPort;
    }

    @Override
    public void validateOrderCustomer(Integer orderId, String customerId) {
        Order order =
                orderPort
                        .findById(orderId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getCustomer() == null
                || !customerId.equals(order.getCustomer().getCustomerId())) {
            throw new IllegalArgumentException("Payment customer must match order customer.");
        }
    }
}
