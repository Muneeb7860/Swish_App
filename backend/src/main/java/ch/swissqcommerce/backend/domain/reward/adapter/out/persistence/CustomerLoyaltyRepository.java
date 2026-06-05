package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomerLoyaltyRepository extends JpaRepository<CustomerLoyalty, Integer> {
    List<CustomerLoyalty> findByCustomerId(String customerId);
}
