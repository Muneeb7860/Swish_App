package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerLoyaltyRepository extends JpaRepository<CustomerLoyaltyEntity, Integer> {
    List<CustomerLoyaltyEntity> findByCustomerId(String customerId);
}
