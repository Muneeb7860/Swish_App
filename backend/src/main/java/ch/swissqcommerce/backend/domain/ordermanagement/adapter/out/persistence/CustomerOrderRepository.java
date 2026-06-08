package ch.swissqcommerce.backend.domain.ordermanagement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrderEntity, String> {
}
