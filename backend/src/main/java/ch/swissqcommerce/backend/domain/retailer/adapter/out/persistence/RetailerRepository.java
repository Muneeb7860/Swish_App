package ch.swissqcommerce.backend.domain.retailer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetailerRepository extends JpaRepository<RetailerEntity, String> {
    Optional<RetailerEntity> findByApiKeyHash(String apiKeyHash);
}
