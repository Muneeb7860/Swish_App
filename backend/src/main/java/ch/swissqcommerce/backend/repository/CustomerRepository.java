package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByHashedEmail(String hashedEmail);

    Optional<Customer> findByEmail(String email);
}
