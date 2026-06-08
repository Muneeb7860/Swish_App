package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfileEntity, String> {
}
