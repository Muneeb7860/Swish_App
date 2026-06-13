package ch.swissqcommerce.backend.domain.customer.port.out;

import ch.swissqcommerce.backend.model.Customer;
import java.util.Optional;

public interface CustomerPort {
    Optional<Customer> findCustomerById(String customerId);

    Customer saveCustomer(Customer customer);
}
