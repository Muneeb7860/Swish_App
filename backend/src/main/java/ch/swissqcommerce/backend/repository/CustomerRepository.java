package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByHashedEmail(String hashedEmail);
    Optional<Customer> findByEmail(String email);
}
