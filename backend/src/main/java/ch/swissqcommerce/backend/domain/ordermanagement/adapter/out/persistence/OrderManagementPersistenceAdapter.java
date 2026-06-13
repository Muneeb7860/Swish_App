package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.ordermanagement.core.model.CustomerOrder;
import ch.swissqcommerce.backend.domain.ordermanagement.port.out.CustomerOrderPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderManagementPersistenceAdapter implements CustomerOrderPort {
    private final CustomerOrderRepository repository;

    @Override
    public CustomerOrder save(CustomerOrder order) {
        CustomerOrderEntity entity =
                CustomerOrderEntity.builder()
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
        return repository
                .findById(orderId)
                .map(
                        e ->
                                CustomerOrder.builder()
                                        .orderId(e.getOrderId())
                                        .customerId(e.getCustomerId())
                                        .status(e.getStatus())
                                        .sagaState(e.getSagaState())
                                        .createdAt(e.getCreatedAt())
                                        .build());
    }
}
