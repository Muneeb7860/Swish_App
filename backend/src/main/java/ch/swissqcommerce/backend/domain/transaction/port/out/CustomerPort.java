package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.Customer;
import java.util.Optional;

public interface CustomerPort {
    Optional<Customer> findCustomerById(String id);

    Customer save(Customer customer);
}
