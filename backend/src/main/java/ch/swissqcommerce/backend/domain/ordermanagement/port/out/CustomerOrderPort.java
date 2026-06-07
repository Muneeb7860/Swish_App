package ch.swissqcommerce.backend.domain.ordermanagement.port.out;

import ch.swissqcommerce.backend.domain.ordermanagement.core.model.CustomerOrder;
import java.util.Optional;

public interface CustomerOrderPort {
    CustomerOrder save(CustomerOrder order);
    Optional<CustomerOrder> findById(String orderId);
}
