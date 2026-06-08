package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.customer.port.out.CustomerPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerPersistenceAdapter implements CustomerPort {

    private final CustomerRepository customerRepository;

    @Override
    public Optional<Customer> findCustomerById(String customerId) {
        return customerRepository.findById(customerId);
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
}
