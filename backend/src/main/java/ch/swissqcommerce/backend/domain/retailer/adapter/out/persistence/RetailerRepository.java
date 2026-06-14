package ch.swissqcommerce.backend.domain.retailer.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailerRepository extends JpaRepository<RetailerEntity, String> {
    Optional<RetailerEntity> findByApiKeyHash(String apiKeyHash);

    List<RetailerEntity> findByStatusOrderByCreatedAtAsc(String status);
}
